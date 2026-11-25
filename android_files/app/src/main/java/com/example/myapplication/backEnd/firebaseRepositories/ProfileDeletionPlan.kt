package com.example.myapplication.backEnd.firebaseRepositories

/** Pure deletion plan. Null means delete; other values are fields to update. */
internal object ProfileDeletionPlan {
    /**
     * Builds the Firestore changes needed to remove a profile and its participation.
     *
     * Removes owned campsites and their lottery copies, removes waitlist and winner
     * membership, and promotes the first remaining campsite to the primary lottery copy.
     * When the deleted user owned a surviving lottery, its ownership transfers to the
     * owner of that first campsite. A lottery with no remaining campsites is removed.
     *
     * Affected invitations and reservations are marked CANCELLED with a PROFILE_DELETED
     * reason. Booking schedules remain for history, with affected bookings and campsite
     * availability removed. Deleted user IDs are cleared from retained reservations;
     * the user's notifications and messages involving them are deleted.
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
        val lotteries = docs.filterKeys { it.split('/').let { parts -> parts.size == 4 && parts[2] == "lottery" } }
        val replacements = mutableMapOf<String, Pair<List<String>, String>>()
        lotteries.values.forEach { lottery ->
            val ids = lottery.ids("campsiteIds").filterNot { it in removed }
            val owner = if (lottery["ownerId"] == userId) {
                ids.firstOrNull()?.let { campsites["campsites/$it"]?.get("ownerID") as? String }.orEmpty()
            } else (lottery["ownerId"] as? String).orEmpty()
            replacements[lottery["id"] as? String ?: ""] = ids to owner
        }
        lotteries.forEach { (path, lottery) ->
            val (ids, owner) = replacements.getValue(lottery["id"] as? String ?: "")
            if (path.split('/')[1] in removed || ids.isEmpty()) changes[path] = null
            else {
                val fields = mapOf("campsiteIds" to ids, "ownerId" to owner,
                    "isPrimary" to (path.split('/')[1] == ids.first()),
                    "waitlist" to lottery.ids("waitlist").filterNot { it == userId },
                    "winners" to lottery.ids("winners").filterNot { it == userId })
                if (fields.any { (key, value) -> lottery[key] != value }) changes[path] = fields
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
                    if (bookings != data["bookings"]) fields["bookings"] = bookings
                    val ids = if (orphanedOwner) emptyList() else data.ids("campsiteIds").filterNot { it in removed }
                    if (ids != data["campsiteIds"]) fields["campsiteIds"] = ids
                    val periods = (data["campsitePeriods"] as? Map<*, *>).orEmpty().filterKeys { !orphanedOwner && it !in removed }
                    if (periods != data["campsitePeriods"]) fields["campsitePeriods"] = periods
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
                "notifications" -> if (data["userId"] == userId) changes[path] = null
                "messages" -> if (data["senderId"] == userId || data["recipientId"] == userId) changes[path] = null
            }
        }
        changes["users/$userId"] = null
        return changes
    }
}
