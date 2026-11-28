package com.example.myapplication.features.profile

/** Pure deletion plan. Null means delete; other values are fields to update. */
internal object ProfileDeletionPlan {
    data class Discovery(val paths: Set<String>, val structuralLotteryPaths: Set<String>)

    private fun String.isLotteryPath(): Boolean =
        split('/').let { parts -> parts.size == 4 && parts[2] == "lottery" }

    /** Includes records the plan changes and the current copies/owners it depends on. */
    fun discover(userId: String, docs: Map<String, Map<String, Any?>>): Discovery {
        val changes = build(userId, docs, now = 0L)
        val changedLotteryIds = changes.keys.filter { it.isLotteryPath() }
            .mapNotNullTo(mutableSetOf()) { docs[it]?.get("id") as? String }
        val lotteryCopies = docs.filter { (path, data) ->
            path.isLotteryPath() && data["id"] in changedLotteryIds
        }
        val campsitePaths = lotteryCopies.flatMap { (path, data) ->
            val ids = (data["campsiteIds"] as? List<*>).orEmpty().filterIsInstance<String>()
                .filter { it.isNotBlank() && '/' !in it }
            (ids + path.split('/')[1]).map { "campsites/$it" }
        }
        val removedCampsites = changes.filter { (path, fields) ->
            fields == null && path.startsWith("campsites/") && path.count { it == '/' } == 1
        }.keys.mapTo(mutableSetOf()) { it.substringAfter('/') }
        val structuralPaths = structurallyChangedLotteryPaths(changes)
        val structuralLotteries = structuralPaths.map { docs.getValue(it) }
        val structuralIds = structuralLotteries.mapNotNullTo(mutableSetOf()) { it["id"] as? String }
        val affectedMembers = structuralLotteries.flatMap { lottery ->
            (lottery["waitlist"] as? List<*>).orEmpty() + (lottery["winners"] as? List<*>).orEmpty()
        }.filterIsInstance<String>().filter { it.isNotBlank() && '/' !in it }.map { "users/$it" }
        val possibleInvitations = structuralLotteries.flatMap { lottery ->
            val id = (lottery["id"] as? String)?.takeIf { it.isNotBlank() && '/' !in it }
                ?: return@flatMap emptyList()
            (lottery["winners"] as? List<*>).orEmpty().filterIsInstance<String>()
                .filter { it.isNotBlank() && '/' !in it }
                .map { "reservations/${id}_$it" }
        }
        val affectedReservations = docs.filter { (path, data) ->
            path.startsWith("reservations/") &&
                (path in changes || data["entrantId"] == userId || data["ownerId"] == userId ||
                    data["campsiteId"] in removedCampsites || data["lotteryId"] in structuralIds)
        }
        // An invitation can become a booking after discovery. Read its schedule even
        // if the current plan has no booking to release.
        val scheduleIds = affectedReservations.values.mapNotNullTo(mutableSetOf()) { it["lotteryId"] as? String }
        val schedulePaths = docs.filter { (path, data) ->
            path.startsWith("bookingSchedules/") &&
                (data["lotteryId"] in scheduleIds || path.substringAfter('/') in scheduleIds)
        }.keys
        // A pending result returned by the recipient query can disappear before the
        // transaction. Its ID still identifies notification and invitation paths to reread.
        val pending = docs.keys.filter { it.startsWith("lotteryDrawResults/") }
        val paths = changes.keys + lotteryCopies.keys + campsitePaths + affectedMembers + affectedReservations.keys +
            possibleInvitations + schedulePaths + pending
        return Discovery(paths, structuralPaths)
    }

    /** If the lottery graph moved, reuse the original transaction only after rediscovery. */
    fun structuralRelationshipsMatch(
        structuralLotteryPaths: Set<String>,
        initial: Map<String, Map<String, Any?>>,
        current: Map<String, Map<String, Any?>>
    ): Boolean = structuralLotteryPaths.all { path ->
        current.containsKey(path) && listOf("id", "ownerId", "campsiteIds", "waitlist", "winners", "drawn")
            .all { field -> initial[path]?.get(field) == current[path]?.get(field) }
    }

    private fun structurallyChangedLotteryPaths(changes: Map<String, Map<String, Any?>?>): Set<String> =
        changes.filter { (path, fields) ->
            path.isLotteryPath() && (fields == null || "campsiteIds" in fields || "ownerId" in fields)
        }.keys

    /** Finds the result paths a profile cleanup must read, including results not yet committed. */
    fun participatingLotteryIds(userId: String, docs: Map<String, Map<String, Any?>>): Set<String> =
        docs.asSequence()
            .filter { (path, data) ->
                path.isLotteryPath() && (userId in (data["waitlist"] as? List<*>).orEmpty() ||
                    userId in (data["winners"] as? List<*>).orEmpty())
            }
            .mapNotNull { (_, data) -> (data["id"] as? String)?.takeIf { it.isNotBlank() && '/' !in it } }
            .toSet()

    /**
     * Builds the Firestore changes needed to remove a profile and its participation.
     *
     * Removes owned campsites and their lottery copies, removes waitlist and winner
     * membership and creator identifiers, and promotes the first remaining campsite
     * to the primary lottery copy. Unrelated records and absent optional fields are preserved.
     * When the deleted user owned a surviving lottery, its ownership transfers to the
     * owner of that first campsite. A lottery with no remaining campsites is removed.
     *
     * Affected invitations and reservations are marked CANCELLED with a PROFILE_DELETED
     * reason. Already cancelled/rejected reservations keep their status and timestamp,
     * but receive this reason to revoke rebooking/restoration. Booking schedules remain
     * for history, with affected bookings and campsite
     * availability removed. Deleted user IDs are cleared from retained reservations;
     * the user's notifications and messages involving them are deleted. Pending
     * lottery results drop the user and are removed when no recipients remain.
     *
     * This function only builds a plan; it does not mutate [docs] or write to Firestore.
     * Cleanup covers the supplied documents, so callers must load all relevant records.
     *
     * @param userId Firestore document ID of the profile being deleted.
     * @param docs Document fields keyed by their full Firestore document paths.
     * @param now Unix time in milliseconds used for reservation cancellation timestamps.
     * @return Changes keyed by document path. A null value deletes the document;
     * a non-null map contains only the fields to update on an existing document.
     * @throws IllegalArgumentException If [userId] is blank or contains a slash.
     */
    fun build(userId: String, docs: Map<String, Map<String, Any?>>, now: Long): Map<String, Map<String, Any?>?> {
        require(userId.isNotBlank() && !userId.contains('/'))
        val changes = linkedMapOf<String, Map<String, Any?>?>()
        /**
         * Reads string IDs from a stored list, ignoring entries of other types.
         *
         * @param key Name of the list field in this document.
         * @return String entries in stored order, or an empty list for a missing/non-list field.
         */
        fun Map<String, Any?>.ids(key: String) = (get(key) as? List<*>)?.filterIsInstance<String>().orEmpty()
        val campsites = docs.filterKeys { it.startsWith("campsites/") && it.count { c -> c == '/' } == 1 }
        val removed = campsites.filterValues { it["ownerID"] == userId }.keys.map { it.substringAfter('/') }.toSet()
        val lotteries = docs.filterKeys { it.isLotteryPath() }
        val replacements = mutableMapOf<String, Pair<List<String>, String>>()
        lotteries.values.forEach { lottery ->
            val originalIds = lottery.ids("campsiteIds")
            // Only structural changes affect schedules and other users' references.
            // Unrelated legacy/orphan lotteries may legitimately have no campsite IDs.
            if (lottery["ownerId"] == userId || originalIds.any { it in removed }) {
                val ids = originalIds.filterNot { it in removed }
                val owner = if (lottery["ownerId"] == userId) {
                    ids.firstOrNull()?.let { campsites["campsites/$it"]?.get("ownerID") as? String }.orEmpty()
                } else (lottery["ownerId"] as? String).orEmpty()
                replacements[lottery["id"] as? String ?: ""] = ids to owner
            }
        }
        lotteries.forEach { (path, lottery) ->
            val originalIds = lottery.ids("campsiteIds")
            val replacement = replacements[lottery["id"] as? String ?: ""]
            val ids = replacement?.first ?: originalIds
            val referencedCampsiteRemoved = originalIds.any { it in removed }
            if (path.split('/')[1] in removed || (referencedCampsiteRemoved && ids.isEmpty())) {
                changes[path] = null
            } else {
                val fields = linkedMapOf<String, Any?>()
                if (ids != originalIds) fields["campsiteIds"] = ids
                if (lottery["ownerId"] == userId) fields["ownerId"] = replacement?.second.orEmpty()
                if (referencedCampsiteRemoved && ids.isNotEmpty()) {
                    val isPrimary = path.split('/')[1] == ids.first()
                    if (lottery["isPrimary"] != isPrimary) fields["isPrimary"] = isPrimary
                }
                listOf("waitlist", "winners").forEach { key ->
                    val members = lottery[key] as? List<*>
                    if (members?.contains(userId) == true) fields[key] = members.filterNot { it == userId }
                }
                if (lottery["createdById"] == userId) fields["createdById"] = ""
                if (fields.isNotEmpty()) changes[path] = fields
            }
        }
        docs.forEach { (path, data) ->
            when (path.substringBefore('/')) {
                "campsites" -> if (path.count { it == '/' } == 1 && path.substringAfter('/') in removed) changes[path] = null
                "bookingSchedules" -> {
                    val replacement = replacements[data["lotteryId"]]
                    val fields = linkedMapOf<String, Any?>()
                    val orphanedOwner = data["ownerId"] == userId && replacement == null
                    val bookings = (data["bookings"] as? List<*>).orEmpty().filterNot { booking ->
                        val b = booking as? Map<*, *> ?: return@filterNot false
                        b["entrantId"] == userId || b["campsiteId"] in removed || replacement?.first?.isEmpty() == true || orphanedOwner
                    }
                    if (data.containsKey("bookings") && bookings != data["bookings"]) fields["bookings"] = bookings
                    val ids = if (orphanedOwner) emptyList() else data.ids("campsiteIds").filterNot { it in removed }
                    if (data.containsKey("campsiteIds") && ids != data["campsiteIds"]) fields["campsiteIds"] = ids
                    val periods = (data["campsitePeriods"] as? Map<*, *>).orEmpty().filterKeys { !orphanedOwner && it !in removed }
                    if (data.containsKey("campsitePeriods") && periods != data["campsitePeriods"]) fields["campsitePeriods"] = periods
                    if (data["ownerId"] == userId) fields["ownerId"] = replacement?.second.orEmpty()
                    if (fields.isNotEmpty()) changes[path] = fields
                }
                "reservations" -> {
                    val replacement = replacements[data["lotteryId"]]
                    val fields = linkedMapOf<String, Any?>()
                    val affected = data["entrantId"] == userId || data["campsiteId"] in removed ||
                        replacement?.first?.isEmpty() == true || (data["ownerId"] == userId && replacement == null)
                    if (affected && data["status"] in listOf("INVITED", "RESERVED")) {
                        fields["status"] = "CANCELLED"
                        fields["cancellationReason"] = "PROFILE_DELETED"
                        fields["updatedAt"] = now
                    } else if (affected && data["status"] in listOf("CANCELLED", "REJECTED") &&
                        data["cancellationReason"] != "PROFILE_DELETED") {
                        // Keep terminal history while revoking rebooking/restoration rights.
                        fields["cancellationReason"] = "PROFILE_DELETED"
                    }
                    if (data["entrantId"] == userId) fields["entrantId"] = ""
                    if (data["ownerId"] == userId) fields["ownerId"] = replacement?.second.orEmpty()
                    if (fields.isNotEmpty()) changes[path] = fields
                }
                "users" -> if (path == "users/$userId") changes[path] = null else {
                    val joined = (data["joinedLotteries"] as? List<*>).orEmpty().mapNotNull { item ->
                        val entry = item as? Map<*, *> ?: return@mapNotNull item
                        val replacement = replacements[entry["lotteryId"]]
                        if (replacement?.first?.isEmpty() == true) null
                        else if (entry["campsiteName"] in removed && replacement != null)
                            entry.entries.associate { it.key.toString() to it.value } + ("campsiteName" to replacement.first.first())
                        else if (entry["campsiteName"] in removed) null else item
                    }
                    if (data.containsKey("joinedLotteries") && joined != data["joinedLotteries"]) changes[path] = mapOf("joinedLotteries" to joined)
                }
                "lotteryDrawResults" -> {
                    val recipients = data["remainingEntrantIds"] as? List<*>
                    val winners = data["winnerIds"] as? List<*>
                    val fields = linkedMapOf<String, Any?>()
                    if (recipients != null && userId in recipients) {
                        val remaining = recipients.filterNot { it == userId }
                        if (remaining.isEmpty()) {
                            changes[path] = null
                            return@forEach
                        }
                        fields["remainingEntrantIds"] = remaining
                    }
                    if (winners != null && userId in winners) {
                        fields["winnerIds"] = winners.filterNot { it == userId }
                    }
                    if (fields.isNotEmpty()) changes[path] = fields
                }
                "notifications" -> if (data["userId"] == userId) changes[path] = null
                "messages" -> if (data["senderId"] == userId || data["recipientId"] == userId) changes[path] = null
            }
        }
        changes["users/$userId"] = null
        return changes
    }
}
