package com.example.myapplication.features

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * A lottery can cover several campsites. Each campsite stores its own copy of the
 * lottery document, and the first campsite in the list holds the "primary" copy.
 *
 * @property primary the Firestore snapshot of the primary copy of the lottery
 * @property campsiteIds the IDs of every campsite that holds a copy of the lottery
 */
private class LotteryCopies(
    val primary: DocumentSnapshot,
    val campsiteIds: List<String>
)

/**
 * Reads and writes lotteries in Firestore.
 *
 * Lotteries are stored at campsites/{campsiteName}/lottery/current. A lottery that
 * covers several campsites is stored once per campsite, and every copy is kept in sync
 * by doing all changes inside Firestore transactions.
 *
 * @property db the Firestore instance to use
 */
class LotteryRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    /** The "campsites" collection in Firestore. */
    private val campsiteRef = db.collection("campsites")

    /** The time zone used for registration dates and times. */
    private val campsiteZone = ZoneId.of("America/Edmonton")

    /**
     * Gets the reference to a campsite's lottery document.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @return the reference to campsites/{campsiteName}/lottery/current
     */
    private fun lotteryDoc(campsiteName: String) =
        campsiteRef.document(campsiteName).collection("lottery").document("current")

    // ---------------------------------------------------------------------
    // Checking if campsites are in a lottery
    // ---------------------------------------------------------------------

    /**
     * Checks if a campsite is already in a lottery.
     *
     * The inLottery flag is stored on the campsite document in Firestore, but it is
     * not part of the Campsite class, so this has to ask Firestore for it.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @param onResult gets true if the campsite exists and is in a lottery, false if it
     * is not in a lottery, does not exist, or the lookup failed
     */
    fun isInLottery(campsiteName: String, onResult: (Boolean) -> Unit) {
        if (!isDocumentId(campsiteName)) {
            onResult(false)
            return
        }

        campsiteRef.document(campsiteName).get()
            .addOnSuccessListener { doc ->
                onResult(doc.getBoolean("inLottery") == true)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    /**
     * Checks if any of the campsites can't be put in a new lottery.
     *
     * @param names the campsite names to check
     * @param onResult gets true if at least one campsite does not exist or is already
     * in a lottery, false if all of them are free. It also gets true if the lookup
     * failed, so a failed check never lets a campsite into two lotteries.
     */
    fun anyInLottery(names: List<String>, onResult: (Boolean) -> Unit) {
        if (names.any { !isDocumentId(it) }) {
            onResult(true)
            return
        }

        val lookups = names.map { campsiteRef.document(it).get() }
        Tasks.whenAllSuccess<DocumentSnapshot>(lookups)
            .addOnSuccessListener { docs ->
                val blocked = docs.any { !it.exists() || it.getBoolean("inLottery") == true }
                onResult(blocked)
            }
            .addOnFailureListener {
                onResult(true)
            }
    }

    // ---------------------------------------------------------------------
    // Drawing winners
    // ---------------------------------------------------------------------

//    /** Draws winners after registration closes and saves them to every copy. */
//    fun drawLotteryWinners(campsiteName: String, onResult: (List<String>?) -> Unit) {
//        if (!isDocumentId(campsiteName)) {
//            onResult(null)
//            return
//        }
//
//        drawLotteryWinnersTask(campsiteName)
//            .addOnSuccessListener { winners -> onResult(winners) }
//            .addOnFailureListener { onResult(null) }
//    }
//
//    /**
//     * Draws every eligible lottery and returns the new winners by primary campsite ID.
//     * Each draw commits on its own. A lookup or transaction error fails the returned task.
//     */
//    fun drawClosedLotteries(): Task<Map<String, List<String>>> {
//        return campsiteRef.whereEqualTo("inLottery", true).get(Source.SERVER)
//            .onSuccessTask { snapshot ->
//                val drawTasks = snapshot.documents.map { campsite ->
//                    drawLotteryWinnersTask(campsite.id, primaryOnly = true)
//                        .continueWith { draw -> campsite.id to draw.result }
//                }
//                Tasks.whenAllSuccess<Pair<String, List<String>?>>(drawTasks)
//            }
//            .continueWith { allDraws ->
//                val newWinners = mutableMapOf<String, List<String>>()
//                for ((campsiteId, winners) in allDraws.result) {
//                    if (winners != null) {
//                        newWinners[campsiteId] = winners
//                    }
//                }
//                newWinners
//            }
//    }
//
//    private fun drawLotteryWinnersTask(
//        campsiteName: String,
//        primaryOnly: Boolean = false
//    ): Task<List<String>?> {
//        return db.runTransaction<List<String>?> { transaction ->
//            val copies = readLotteryCopies(transaction, campsiteName, primaryOnly)
//                ?: return@runTransaction null
//            val lottery = readLottery(copies.primary)
//                ?: return@runTransaction null
//            if (!canDraw(lottery)) {
//                return@runTransaction null
//            }
//
//            // Look up the owner. Reads must come before any writes.
//            val primaryCampsiteId = copies.primary.reference.parent.parent?.id ?: campsiteName
//            val ownerId = transaction.get(campsiteRef.document(primaryCampsiteId))
//                .getString("ownerID").orEmpty()
//
//            val winners = lottery.waitlist.distinct().shuffled().take(lottery.numWinners)
//
//            // Save winners to every copy
//            val changes = mapOf("winners" to winners, "drawn" to true)
//            for (id in copies.campsiteIds) {
//                transaction.update(lotteryDoc(id), changes)
//            }
//
//            // One INVITED reservation per winner
//            val drawnLottery = lottery.copy(ownerId = ownerId, winners = winners, drawn = true)
//            for (invitation in drawnLottery.createInvitations()) {
//                val doc = db.collection("reservations").document()
//                invitation.id = doc.id
//                transaction.set(doc, invitation)
//            }
//
//            winners
//        }
//    }

    /**
     * Checks if a lottery is ready to have its winners drawn.
     *
     * @param lottery the lottery to check
     * @return true if the lottery has not been drawn, registration has ended, the
     * waitlist is not empty, and there is at least one winner to pick. Otherwise false.
     */
    private fun canDraw(lottery: Lottery): Boolean {
        val now = LocalDateTime.now(campsiteZone)
        if (lottery.drawn) return false
        if (now.isBefore(lottery.period.registrationEnd)) return false
        if (lottery.waitlist.isEmpty()) return false
        if (lottery.numWinners < 1) return false
        return true
    }

    // ---------------------------------------------------------------------
    // Joining the waitlist
    // ---------------------------------------------------------------------


    /**
     * Removes an entrant or admin from a lottery waitlist.
     *
     * Updates the waitlist on every participating campsite in Firestore
     * and removes the lottery from the user's joinedLotteries.
     *
     * All Firestore changes are performed in one transaction.
     * The local User object is updated only after the transaction succeeds.
     *
     * @param campsiteName The campsite associated with the lottery.
     * @param user The user leaving the waitlist.
     * @param onResult Returns true if the user successfully leaves,
     *                 or false if the operation fails.
     */
    fun leaveLotteryWaitlist(
        campsiteName: String,
        user: User,
        onResult: (Boolean) -> Unit
    ) {
        val entrantId = user.deviceID

        if (!isDocumentId(campsiteName) || !isDocumentId(entrantId)) {
            onResult(false)
            return
        }

        val userRef = db.collection("users").document(entrantId)

        db.runTransaction<String?> { transaction ->

            val copies = readLotteryCopies(transaction, campsiteName)
                ?: return@runTransaction null

            val lottery = readLottery(copies.primary)
                ?: return@runTransaction null

            // Cannot leave after winners have been drawn.
            if (lottery.drawn) {
                return@runTransaction null
            }

            // User must currently be on the waitlist.
            if (entrantId !in lottery.waitlist) {
                return@runTransaction null
            }

            // Read user before performing any writes.
            val userDoc = transaction.get(userRef)

            val updatedWaitlist = lottery.waitlist.filterNot {
                it == entrantId
            }

            // Update every campsite belonging to the lottery.
            for (id in copies.campsiteIds) {
                transaction.update(
                    lotteryDoc(id),
                    "waitlist",
                    updatedWaitlist
                )
            }

            // Update the user's joined lotteries in Firestore.
            if (userDoc.exists()) {
                val joined = (userDoc.get("joinedLotteries") as? List<*>)
                    .orEmpty()
                    .filterNotNull()

                val updatedJoined = joined.filterNot { entry ->
                    (entry as? Map<*, *>)?.get("lotteryId") == lottery.id
                }

                transaction.update(
                    userRef,
                    "joinedLotteries",
                    updatedJoined
                )
            }

            lottery.id

        }.addOnSuccessListener { lotteryId ->

            if (lotteryId != null) {

                // Update the local User object.
                user.joinedLotteries = user.joinedLotteries.filterNot {
                    it.lotteryId == lotteryId
                }

                onResult(true)

            } else {
                onResult(false)
            }

        }.addOnFailureListener {
            onResult(false)
        }
    }



    /**
     * Adds an entrant or admin to the lottery waitlist in Firestore, then updates
     * the supplied User object's joinedLotteries after the transaction succeeds.
     *
     * @param campsiteName the campsite associated with the lottery
     * @param user the user joining the waitlist
     * @param onResult gets true if the user joined, or false if the user is not an
     * entrant or admin, the lottery can't be joined, or the operation failed
     */
    fun joinLotteryWaitlist(
        campsiteName: String,
        user: User,
        onResult: (Boolean) -> Unit
    ) {
        val userType = user.userType.lowercase()
        if (userType != "entrant" && userType != "admin") {
            onResult(false)
            return
        }

        val entrantId = user.deviceID
        if (!isDocumentId(campsiteName) || !isDocumentId(entrantId)) {
            onResult(false)
            return
        }

        db.runTransaction<String?> { transaction ->
            val copies = readLotteryCopies(transaction, campsiteName)
                ?: return@runTransaction null
            val lottery = readLottery(copies.primary)
                ?: return@runTransaction null
            if (!canJoin(lottery, entrantId)) {
                return@runTransaction null
            }

            val updatedWaitlist = lottery.waitlist + entrantId
            for (id in copies.campsiteIds) {
                transaction.update(lotteryDoc(id), "waitlist", updatedWaitlist)
            }

            lottery.id
        }.addOnSuccessListener { lotteryId ->
            if (lotteryId != null) {
                user.joinedLotteries += JoinedLottery(lotteryId, campsiteName)
                onResult(true)
            } else {
                onResult(false)
            }
        }.addOnFailureListener {
            onResult(false)
        }
    }

    /**
     * Checks if an entrant is allowed to join a lottery's waitlist.
     *
     * @param lottery the lottery to join
     * @param entrantId the ID of the user who wants to join
     * @return true if the lottery has not been drawn, registration is open, the user is
     * not already on the waitlist or a winner, and the waitlist is not full.
     * Otherwise false.
     */
    private fun canJoin(lottery: Lottery, entrantId: String): Boolean {
        val now = LocalDateTime.now(campsiteZone)
        if (lottery.drawn) return false
        if (!lottery.period.registrationIsOpen(now)) return false
        if (entrantId in lottery.waitlist) return false
        if (entrantId in lottery.winners) return false
        if (isWaitlistFull(lottery)) return false
        return true
    }

    /**
     * Checks if a lottery's waitlist has reached its capacity.
     *
     * @param lottery the lottery to check
     * @return true if the waitlist has a limit and has reached it, false otherwise
     */
    private fun isWaitlistFull(lottery: Lottery): Boolean {
        // A capacity of 0 means the waitlist has no limit.
        return lottery.waitlistCapacity > 0 && lottery.waitlist.size >= lottery.waitlistCapacity
    }

    // ---------------------------------------------------------------------
    // Creating and saving
    // ---------------------------------------------------------------------

    /**
     * Creates a lottery and returns it after Firestore saves every campsite copy.
     * Saving the lottery also sets inLottery = true on every campsite document that
     * is part of it.
     *
     * @param campsiteName the name of the primary campsite
     * @param period the registration period of the lottery
     * @param numWinners how many winners will be drawn
     * @param campsites the campsites map from CampsiteRepository (name -> campsite). It is
     * used to check that every campsite in the lottery exists, with one lookup each.
     * @param waitlistCapacity the maximum waitlist size, where 0 means no limit
     * @param additionalCampsiteIds other campsites that share this lottery
     * @return a task that finishes with the new lottery, or fails if a campsite does not
     * exist or the lottery could not be saved
     */
    fun createLottery(
        campsiteName: String,
        period: Registration,
        numWinners: Int,
        campsites: Map<String, Campsite>,
        waitlistCapacity: Int = 0,
        additionalCampsiteIds: List<String> = emptyList()
    ): Task<Lottery> {
        val campsiteIds = participatingCampsites(campsiteName, listOf(campsiteName) + additionalCampsiteIds)

        // Look each campsite up in the map to make sure it exists
        for (id in campsiteIds) {
            if (!campsites.containsKey(id)) {
                return Tasks.forException(IllegalArgumentException("Campsite $id does not exist."))
            }
        }

        val lottery = Lottery(
            id = UUID.randomUUID().toString(),
            campsiteIds = campsiteIds,
            period = period,
            numWinners = numWinners,
            waitlistCapacity = waitlistCapacity
        )
        return saveLottery(campsiteName, lottery, createOnly = true)
            .onSuccessTask {
                Tasks.forResult(lottery)
            }
    }

    /**
     * Saves a configured lottery to all participating campsites.
     *
     * @param campsiteName the name of the primary campsite
     * @param lottery the lottery to save
     * @return a task that finishes when every copy is saved, or fails if the lottery is
     * invalid or changed since it was loaded
     */
    fun saveLottery(campsiteName: String, lottery: Lottery): Task<Void> =
        saveLottery(campsiteName, lottery, createOnly = false)

    /**
     * Validates a lottery and saves a copy of it to every participating campsite in one
     * transaction. Also sets inLottery = true on each of those campsites.
     *
     * @param campsiteName the name of the primary campsite
     * @param lottery the lottery to save
     * @param createOnly true if this is a brand new lottery, which means no campsite may
     * already have a lottery and the primary campsite must have an occupancy set
     * @return a task that finishes when every copy is saved, or fails if the lottery is
     * invalid or the checks in the transaction do not pass
     */
    private fun saveLottery(campsiteName: String, lottery: Lottery, createOnly: Boolean): Task<Void> {
        val ids = participatingCampsites(campsiteName, lottery.campsiteIds)
        if (!isDocumentId(campsiteName) || ids.any { !isDocumentId(it) }) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }

        val problem = findLotteryProblem(lottery)
        if (problem != null) {
            return Tasks.forException(IllegalArgumentException(problem))
        }

        val primaryId = ids.first()
        val data = buildLotteryData(lottery, ids)

        return db.runTransaction<Void> { transaction ->
            // Firestore requires all reads to happen before any writes.
            checkOccupancy(transaction, primaryId, lottery.numWinners, createOnly)
            checkExistingCopies(transaction, ids, lottery, createOnly)

            for (id in ids) {
                val isPrimary = (id == primaryId)
                transaction.set(
                    lotteryDoc(id),
                    data + ("isPrimary" to isPrimary),
                    SetOptions.merge()
                )
                transaction.update(campsiteRef.document(id), campsiteLotteryFlags(true))
            }
            null
        }
    }

    /**
     * Returns an error message if the lottery settings are invalid, or null if they are fine.
     *
     * @param lottery the lottery to check
     * @return a message describing the problem, or null if there is none
     */
    private fun findLotteryProblem(lottery: Lottery): String? {
        if (lottery.id.isBlank() || lottery.numWinners < 1 || lottery.waitlistCapacity < 0) {
            return "Set a lottery ID, at least one winner, and a nonnegative waitlist capacity."
        }
        if (!lottery.period.registrationStart.isBefore(lottery.period.registrationEnd)) {
            return "Registration end must be after its start."
        }
        return null
    }

    /**
     * Converts a lottery into the map of fields that is stored in Firestore.
     *
     * Dates are stored as text, plus a Timestamp for the closing time so Firestore can
     * sort and filter on it.
     *
     * @param lottery the lottery to convert
     * @param campsiteIds the IDs of every campsite that holds a copy of the lottery
     * @return the Firestore fields for the lottery
     */
    private fun buildLotteryData(lottery: Lottery, campsiteIds: List<String>): Map<String, Any> {
        val closingTime = lottery.period.registrationEnd.atZone(campsiteZone).toInstant()
        return mapOf(
            "id" to lottery.id,
            "campsiteIds" to campsiteIds,
            "numWinners" to lottery.numWinners,
            "waitlistCapacity" to lottery.waitlistCapacity,
            "waitlist" to lottery.waitlist.toList(),
            "winners" to lottery.winners.toList(),
            "drawn" to lottery.drawn,
            "registrationStart" to lottery.period.registrationStart.toString(),
            "registrationEnd" to lottery.period.registrationEnd.toString(),
            "registrationEndAt" to Timestamp(closingTime.epochSecond, closingTime.nano)
        )
    }

    /**
     * Checks that a campsite's occupancy can support the number of winners.
     * Throws an IllegalStateException if the check fails. Must be called inside a
     * transaction, before any writes.
     *
     * @param transaction the transaction to read with
     * @param primaryId the ID of the campsite to check
     * @param numWinners the number of winners the lottery promises
     * @param createOnly true if the campsite must already have an occupancy set
     */
    private fun checkOccupancy(
        transaction: Transaction,
        primaryId: String,
        numWinners: Int,
        createOnly: Boolean
    ) {
        val occupancy = transaction.get(campsiteRef.document(primaryId)).getLong("occupancy")
        check(!createOnly || occupancy != null) {
            "Set the primary campsite's occupancy before creating a lottery."
        }
        check(occupancy == null || numWinners <= occupancy) {
            "Number of winners exceeds the primary campsite's occupancy."
        }
    }

    /**
     * Makes sure saving will not overwrite a lottery that changed since it was loaded.
     * Throws an IllegalStateException if the check fails. Must be called inside a
     * transaction, before any writes.
     *
     * @param transaction the transaction to read with
     * @param ids the IDs of every campsite that will be saved
     * @param lottery the lottery that is about to be saved
     * @param createOnly true if no campsite is allowed to already have a lottery
     */
    private fun checkExistingCopies(
        transaction: Transaction,
        ids: List<String>,
        lottery: Lottery,
        createOnly: Boolean
    ) {
        val newWaitlist = lottery.waitlist.toList()
        val newWinners = lottery.winners.toList()

        for (id in ids) {
            val saved = transaction.get(lotteryDoc(id))
            if (!saved.exists()) continue

            check(!createOnly && saved.getString("id") == lottery.id) {
                "$id already has a lottery."
            }

            val savedIds = participatingCampsites(id, readIds(saved, "campsiteIds"))
            check(ids.containsAll(savedIds)) {
                "The save must include every existing campsite."
            }

            check(newWaitlist.containsAll(readIds(saved, "waitlist"))) {
                "The waitlist changed. Reload the lottery before saving."
            }

            if (saved.getBoolean("drawn") == true) {
                val sameWinners = newWinners == readIds(saved, "winners")
                check(lottery.drawn && sameWinners) {
                    "The lottery has been drawn. Reload it before saving."
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Focused lottery updates
    // ---------------------------------------------------------------------

    /**
     * Changes registration dates without replacing the rest of the lottery.
     *
     * @param user the user making the change, who must be an admin or own every campsite
     * @param campsiteName the campsite associated with the lottery
     * @param period the new registration period
     * @return a task that finishes when every copy is updated, or fails if the dates are
     * invalid or the user is not allowed to edit the lottery
     */
    fun updateLotteryPeriod(
        user: User,
        campsiteName: String,
        period: Registration
    ): Task<Void> {
        // Registration is mutable; capture the values passed to this call.
        val start = period.registrationStart
        val end = period.registrationEnd
        if (!start.isBefore(end)) {
            return Tasks.forException(IllegalArgumentException("Registration end must be after its start."))
        }

        return editLottery(user, campsiteName) { transaction, copies, saved ->
            val changes = mutableMapOf<String, Any>()
            if (saved.period.registrationStart != start) {
                changes["registrationStart"] = start.toString()
            }
            if (saved.period.registrationEnd != end) {
                val instant = end.atZone(campsiteZone).toInstant()
                changes["registrationEnd"] = end.toString()
                changes["registrationEndAt"] = Timestamp(instant.epochSecond, instant.nano)
            }
            updateLotteryCopies(transaction, copies.campsiteIds, changes)
        }
    }

    /**
     * Changes the waitlist limit. Zero means unlimited.
     *
     * @param user the user making the change, who must be an admin or own every campsite
     * @param campsiteName the campsite associated with the lottery
     * @param waitlistCapacity the new limit, which can't be negative or smaller than the
     * current waitlist unless it is zero
     * @return a task that finishes when every copy is updated, or fails if the capacity is
     * invalid or the user is not allowed to edit the lottery
     */
    fun updateLotteryWaitlistCapacity(
        user: User,
        campsiteName: String,
        waitlistCapacity: Int
    ): Task<Void> {
        if (waitlistCapacity < 0) {
            return Tasks.forException(IllegalArgumentException("Waitlist capacity cannot be negative."))
        }
        return editLottery(user, campsiteName) { transaction, copies, saved ->
            check(waitlistCapacity == 0 || waitlistCapacity >= saved.waitlist.size) {
                "The new capacity cannot be less than the current waitlist size."
            }
            if (waitlistCapacity != saved.waitlistCapacity) {
                updateLotteryCopies(
                    transaction, copies.campsiteIds,
                    mapOf("waitlistCapacity" to waitlistCapacity)
                )
            }
        }
    }

    /**
     * Changes the number of winners; it must fit in every participating campsite.
     *
     * @param user the user making the change, who must be an admin or own every campsite
     * @param campsiteName the campsite associated with the lottery
     * @param numWinners the new number of winners, which must be at least 1
     * @return a task that finishes when every copy is updated, or fails if the number is
     * invalid, too large for a campsite, or the user is not allowed to edit the lottery
     */
    fun updateLotteryNumWinners(
        user: User,
        campsiteName: String,
        numWinners: Int
    ): Task<Void> {
        if (numWinners < 1) {
            return Tasks.forException(IllegalArgumentException("Number of winners must be at least 1."))
        }
        return editLottery(user, campsiteName) { transaction, copies, saved ->
            // Reads happen before any updates, as required by Firestore transactions.
            for (id in copies.campsiteIds) {
                checkOccupancy(transaction, id, numWinners, true)
            }
            if (numWinners != saved.numWinners) {
                updateLotteryCopies(transaction, copies.campsiteIds, mapOf("numWinners" to numWinners))
            }
        }
    }

    /**
     * Replaces the waitlist and synchronizes affected users' joinedLotteries entries.
     * Call only for an administrative edit; entrants should use join/leave operations.
     *
     * @param user the user making the change, who must be an admin or own every campsite
     * @param campsiteName the campsite associated with the lottery
     * @param waitlist the new waitlist as a list of unique user IDs
     * @return a task that finishes when the waitlist and the users are updated, or fails
     * if the list is invalid, exceeds the capacity, or the user is not allowed to edit
     */
    fun updateLotteryWaitlist(
        user: User,
        campsiteName: String,
        waitlist: List<String>
    ): Task<Void> {
        if (waitlist.any { !isDocumentId(it) } || waitlist.size != waitlist.distinct().size) {
            return Tasks.forException(
                IllegalArgumentException("Waitlist must contain unique, valid user IDs.")
            )
        }
        val requested = waitlist.toList()
        return editLottery(user, campsiteName) { transaction, copies, saved ->
            check(saved.waitlistCapacity == 0 || requested.size <= saved.waitlistCapacity) {
                "The waitlist exceeds its capacity."
            }
            check(requested.none { it in saved.winners }) {
                "A winner cannot also be on the waitlist."
            }

            val added = requested.filterNot { it in saved.waitlist }
            val removed = saved.waitlist.filterNot { it in requested }
            // Read every affected user before writing any campsite or user document.
            val userSnapshots = (added + removed).distinct().associateWith { id ->
                transaction.get(db.collection("users").document(id))
            }
            val userChanges = mutableMapOf<String, List<Any>>()
            for (id in added) {
                val doc = checkNotNull(userSnapshots[id])
                check(doc.exists()) { "User $id does not exist." }
                val role = doc.getString("userType").orEmpty().lowercase()
                check(role == "entrant" || role == "admin") {
                    "Only entrants and admins may join the waitlist."
                }
            }
            for (id in (added + removed).distinct()) {
                val doc = checkNotNull(userSnapshots[id])
                if (!doc.exists()) continue // A removed user may no longer have an account.
                val joins = (doc.get("joinedLotteries") as? List<*>)
                    .orEmpty()
                    .filterNotNull()
                val withoutThisLottery = joins.filterNot { entry ->
                    (entry as? Map<*, *>)?.get("lotteryId") == saved.id
                }
                val newJoins: List<Any> = if (id in added) {
                    withoutThisLottery + mapOf(
                        "lotteryId" to saved.id,
                        "campsiteName" to campsiteName
                    )
                } else {
                    withoutThisLottery
                }
                if (newJoins != joins) userChanges[id] = newJoins
            }

            if (requested != saved.waitlist) {
                updateLotteryCopies(transaction, copies.campsiteIds, mapOf("waitlist" to requested))
            }
            for ((id, joins) in userChanges) {
                transaction.update(db.collection("users").document(id), "joinedLotteries", joins)
            }
        }
    }

    /**
     * Adds another campsite to the same lottery. The campsite must be unused,
     * owned by this user (unless admin), and large enough for the draw.
     *
     * @param user the user making the change
     * @param campsiteName a campsite that is already in the lottery
     * @param newCampsiteName the campsite to add
     * @return a task that finishes when the campsite is added, or fails if the campsite is
     * invalid, already in a lottery, too small, or the user is not allowed to add it
     */
    fun addCampsiteToLottery(
        user: User,
        campsiteName: String,
        newCampsiteName: String
    ): Task<Void> {
        if (!isDocumentId(newCampsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid new campsite ID."))
        }
        return editLottery(user, campsiteName) { transaction, copies, saved ->
            check(newCampsiteName !in copies.campsiteIds) {
                "This campsite is already part of the lottery."
            }

            val newCampsiteRef = campsiteRef.document(newCampsiteName)
            val newCampsite = transaction.get(newCampsiteRef)
            val existingLottery = transaction.get(lotteryDoc(newCampsiteName))
            check(newCampsite.exists()) { "The new campsite does not exist." }
            check(newCampsite.getBoolean("inLottery") != true && !existingLottery.exists()) {
                "The new campsite is already assigned to a lottery."
            }
            check(canEditCampsite(user, newCampsite)) {
                "Only an admin or the new campsite's owner can add it."
            }
            val occupancy = newCampsite.getLong("occupancy")
            check(occupancy != null && occupancy >= saved.numWinners) {
                "The new campsite occupancy must be at least ${saved.numWinners}."
            }

            val updatedIds = copies.campsiteIds + newCampsiteName
            updateLotteryCopies(transaction, copies.campsiteIds, mapOf("campsiteIds" to updatedIds))
            transaction.set(
                lotteryDoc(newCampsiteName),
                buildLotteryData(saved, updatedIds) + ("isPrimary" to false)
            )
            transaction.update(newCampsiteRef, campsiteLotteryFlags(true))
        }
    }

    /**
     * Removes one campsite from a shared lottery, leaving its other campsites intact.
     * The last campsite cannot be removed here; use deleteLottery instead.
     *
     * @param user the user making the change, who must be an admin or own every campsite
     * @param campsiteName a campsite that is in the lottery
     * @param removeCampsiteName the campsite to remove
     * @return a task that finishes when the campsite is removed, or fails if the campsite
     * is not in the lottery, is the last one, or the user is not allowed to edit
     */
    fun removeCampsiteFromLottery(
        user: User,
        campsiteName: String,
        removeCampsiteName: String
    ): Task<Void> {
        if (!isDocumentId(removeCampsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID to remove."))
        }
        return editLottery(user, campsiteName) { transaction, copies, saved ->
            check(removeCampsiteName in copies.campsiteIds) {
                "The campsite is not in this lottery."
            }
            check(copies.campsiteIds.size > 1) {
                "Cannot remove the last campsite. Use deleteLottery instead."
            }
            val remainingIds = copies.campsiteIds.filterNot { it == removeCampsiteName }
            val newPrimary = remainingIds.first()
            // The new primary must support the promised number of winners.
            if (removeCampsiteName == copies.campsiteIds.first()) {
                checkOccupancy(transaction, newPrimary, saved.numWinners, true)
            }

            // Entries made through the removed campsite should point to one that remains.
            // Read every affected user before any writes to keep join references consistent.
            val migratedJoins = mutableMapOf<String, List<Any>>()
            for (id in saved.waitlist.distinct()) {
                check(isDocumentId(id)) { "An invalid entrant ID exists on the waitlist." }
                val userDoc = transaction.get(db.collection("users").document(id))
                if (!userDoc.exists()) continue
                val joins = (userDoc.get("joinedLotteries") as? List<*>)
                    .orEmpty().filterNotNull()
                val changed = joins.map { entry ->
                    val info = entry as? Map<*, *>
                    if (info?.get("lotteryId") == saved.id &&
                        info?.get("campsiteName") == removeCampsiteName
                    ) {
                        mapOf("lotteryId" to saved.id, "campsiteName" to newPrimary)
                    } else {
                        entry
                    }
                }.distinct()
                if (joins != changed) migratedJoins[id] = changed
            }

            // All Firestore reads are finished. Keep every remaining copy in sync.
            for (id in remainingIds) {
                transaction.update(
                    lotteryDoc(id),
                    mapOf(
                        "campsiteIds" to remainingIds,
                        "isPrimary" to (id == newPrimary)
                    )
                )
            }
            transaction.delete(lotteryDoc(removeCampsiteName))
            transaction.update(campsiteRef.document(removeCampsiteName), campsiteLotteryFlags(false))
            for ((id, joins) in migratedJoins) {
                transaction.update(db.collection("users").document(id), "joinedLotteries", joins)
            }
        }
    }

    /**
     * Shared editing checks: all copies must agree, the lottery must not be drawn,
     * and the caller must be an admin or own ALL participating campsites.
     *
     * @param user the user making the change
     * @param campsiteName a campsite that is in the lottery
     * @param edit the change to make, which gets the transaction, the lottery copies and
     * the saved lottery. It must do all of its reads before any writes.
     * @return a task that finishes when the transaction commits, or fails if a check
     * does not pass
     */
    private fun editLottery(
        user: User,
        campsiteName: String,
        edit: (Transaction, LotteryCopies, Lottery) -> Unit
    ): Task<Void> {
        if (!isDocumentId(campsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }
        return db.runTransaction<Void> { transaction ->
            val copies = checkNotNull(readLotteryCopies(transaction, campsiteName)) {
                "The lottery was not found or its copies are inconsistent."
            }
            val saved = checkNotNull(readLottery(copies.primary)) {
                "The lottery was not found."
            }
            // Read all owner records before invoking an edit that could perform writes.
            val campsites = copies.campsiteIds.map { id ->
                transaction.get(campsiteRef.document(id))
            }
            check(campsites.all { it.exists() && canEditCampsite(user, it) }) {
                "Only an admin or an owner of every participating campsite can edit this lottery."
            }
            check(!saved.drawn) { "The lottery has been drawn and cannot be edited." }
            edit(transaction, copies, saved)
            null
        }
    }

    /**
     * Checks if a user is allowed to change a campsite's lottery.
     *
     * @param user the user to check
     * @param campsite the Firestore snapshot of the campsite
     * @return true if the user is an admin or is the campsite's owner, false otherwise
     */
    private fun canEditCampsite(user: User, campsite: DocumentSnapshot): Boolean =
        user.userType.equals("admin", ignoreCase = true) ||
                (user.deviceID.isNotBlank() && campsite.getString("ownerID") == user.deviceID)

    /**
     * Applies the same changes to the lottery document of every campsite in the lottery.
     * Does nothing if there are no changes.
     *
     * @param transaction the transaction to write with
     * @param campsiteIds the IDs of the campsites whose copies should be updated
     * @param changes the fields to update and their new values
     */
    private fun updateLotteryCopies(
        transaction: Transaction,
        campsiteIds: List<String>,
        changes: Map<String, Any>
    ) {
        if (changes.isEmpty()) return
        for (id in campsiteIds) {
            transaction.update(lotteryDoc(id), changes)
        }
    }

    // ---------------------------------------------------------------------
    // Loading and deleting
    // ---------------------------------------------------------------------

    /**
     * Loads the current lottery for a campsite.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @param onResult gets the lottery, or null if it does not exist, can't be read,
     * or the lookup failed
     */
    fun getLottery(campsiteName: String, onResult: (Lottery?) -> Unit) {
        if (!isDocumentId(campsiteName)) {
            onResult(null)
            return
        }

        lotteryDoc(campsiteName).get().addOnSuccessListener { doc ->
            val lottery = try {
                readLottery(doc)
            } catch (_: Exception) {
                null
            }
            onResult(lottery)
        }.addOnFailureListener {
            onResult(null)
        }
    }

    /**
     * Deletes all copies of the current lottery and clears the campsite flags.
     *
     * @param campsiteName a campsite that is in the lottery
     * @return a task that finishes when every copy is deleted, or fails if the campsite
     * ID is invalid or the lottery is missing or inconsistent
     */
    fun deleteLottery(campsiteName: String): Task<Void> {
        if (!isDocumentId(campsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }

        return db.runTransaction<Void> { transaction ->
            val copies = checkNotNull(readLotteryCopies(transaction, campsiteName)) {
                "The lottery or one of its campsite copies is missing or inconsistent."
            }
            for (id in copies.campsiteIds) {
                transaction.delete(lotteryDoc(id))
                transaction.update(campsiteRef.document(id), campsiteLotteryFlags(false))
            }
            null
        }
    }

    // ---------------------------------------------------------------------
    // Reading from Firestore
    // ---------------------------------------------------------------------

    /**
     * Reads the primary copy and checks that every copy belongs to the same lottery.
     * Returns null if anything is missing or inconsistent.
     * If primaryOnly is true, campsiteName must be the primary campsite.
     *
     * @param transaction the transaction to read with
     * @param campsiteName a campsite that is in the lottery
     * @param primaryOnly true if campsiteName must be the primary campsite
     * @return the primary copy and the IDs of every campsite in the lottery, or null
     */
    private fun readLotteryCopies(
        transaction: Transaction,
        campsiteName: String,
        primaryOnly: Boolean = false
    ): LotteryCopies? {
        val selected = transaction.get(lotteryDoc(campsiteName))
        val lotteryId = selected.getString("id")
        if (lotteryId.isNullOrBlank()) return null

        // The first campsite in the stored list holds the primary copy.
        val storedIds = readIds(selected, "campsiteIds")
        val primaryId = storedIds.firstOrNull { it.isNotBlank() } ?: campsiteName
        if (!isDocumentId(primaryId)) return null
        if (primaryOnly && primaryId != campsiteName) return null

        val primary = if (primaryId == campsiteName) selected else transaction.get(lotteryDoc(primaryId))
        if (primary.getString("id") != lotteryId) return null

        val allIds = participatingCampsites(campsiteName, readIds(primary, "campsiteIds") + primaryId)
        if (allIds.any { !isDocumentId(it) }) return null

        for (id in allIds) {
            val copy = when (id) {
                campsiteName -> selected
                primaryId -> primary
                else -> transaction.get(lotteryDoc(id))
            }
            if (copy.getString("id") != lotteryId) return null
        }

        return LotteryCopies(primary, allIds)
    }

    /**
     * Converts a Firestore lottery document into a Lottery object.
     * Missing numbers fall back to defaults (1 winner, no waitlist limit, not drawn).
     *
     * @param doc the Firestore snapshot of a lottery document
     * @return the lottery, or null if the document does not exist
     */
    private fun readLottery(doc: DocumentSnapshot): Lottery? {
        if (!doc.exists()) return null

        val period = Registration(
            registrationStart = readDate(doc, "registrationStart"),
            registrationEnd = readDate(doc, "registrationEnd")
        )
        return Lottery(
            id = doc.getString("id").orEmpty(),
            campsiteIds = readIds(doc, "campsiteIds"),
            period = period,
            numWinners = doc.getLong("numWinners")?.toInt() ?: 1,
            waitlistCapacity = doc.getLong("waitlistCapacity")?.toInt() ?: 0,
            waitlist = readIds(doc, "waitlist"),
            winners = readIds(doc, "winners"),
            drawn = doc.getBoolean("drawn") ?: false
        )
    }

    /**
     * Reads a date that is stored as text in a Firestore document.
     *
     * @param doc the Firestore snapshot to read from
     * @param field the name of the field that holds the date text
     * @return the parsed date and time
     * @throws IllegalArgumentException if the field is missing
     */
    private fun readDate(doc: DocumentSnapshot, field: String): LocalDateTime {
        val text = requireNotNull(doc.getString(field)) { "Missing $field" }
        return LocalDateTime.parse(text)
    }

    /**
     * Reads a list of strings from a Firestore document.
     *
     * @param doc the Firestore snapshot to read from
     * @param field the name of the field that holds the list
     * @return the strings in the list, or an empty list if the field is missing or is
     * not a list. Items that are not strings are skipped.
     */
    private fun readIds(doc: DocumentSnapshot, field: String): List<String> {
        val list = doc.get(field) as? List<*> ?: return emptyList()
        return list.filterIsInstance<String>()
    }

    // ---------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------

    /**
     * Combines the IDs with the campsite name, dropping blanks and duplicates.
     *
     * @param campsiteName the name of a campsite that must be included
     * @param campsiteIds the other campsite IDs
     * @return the combined list of IDs, without blanks or duplicates
     */
    private fun participatingCampsites(campsiteName: String, campsiteIds: List<String>): List<String> =
        (campsiteIds + campsiteName).filter { it.isNotBlank() }.distinct()

    /**
     * Builds the campsite fields that mark whether a campsite is in a lottery.
     *
     * @param active true if the campsite is in a lottery, false if it is not
     * @return the map to write to the campsite document
     */
    private fun campsiteLotteryFlags(active: Boolean) =
        mapOf("inLottery" to active)

    /**
     * Checks that a value can be used as a Firestore document ID in this repository.
     *
     * @param value the text to check
     * @return true if it is not blank and has no "/" in it, false otherwise
     */
    private fun isDocumentId(value: String): Boolean = value.isNotBlank() && '/' !in value
}
