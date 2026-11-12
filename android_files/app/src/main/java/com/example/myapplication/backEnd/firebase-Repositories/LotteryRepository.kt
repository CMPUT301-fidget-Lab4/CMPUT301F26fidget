package com.example.myapplication.backEnd.`firebase-Repositories`

import com.example.myapplication.backEnd.time.AlbertaTime

import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Registration
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import com.example.myapplication.backEnd.userModels.JoinedLottery
import com.example.myapplication.backEnd.userModels.User
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The primary lottery document and the IDs of all campsites sharing it.
 * The first campsite in the stored list holds the primary copy.
 */
private class LotteryCopies(
    val primarySnapshot: DocumentSnapshot,
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
    private val campsitesCollection = db.collection("campsites")

    /** The time zone used for registration dates and times. */
    private val registrationZone = AlbertaTime.zone

    private fun lotteryDocument(campsiteName: String) =
        campsitesCollection.document(campsiteName).collection("lottery").document("current")

    // Drawing winners

    /**
     * Checks active campsites and draws each eligible lottery only once.
     *
     * Only the primary copy is processed. Each draw commits winners, invitations,
     * and cleared campsite flags in one transaction. Previously drawn lotteries
     * with stale active flags are repaired without drawing again.
     *
     * @return a task containing the newly drawn winners, keyed by primary
     * campsite ID. Previously drawn or still-open lotteries are omitted.
     */
    fun drawClosedLotteries(): Task<Map<String, List<String>>> {
        return campsitesCollection.whereEqualTo("inLottery", true).get(Source.SERVER)
            .onSuccessTask { snapshot ->
                val drawTasks = snapshot.documents.map { campsite ->
                    drawLotteryWinnersTask(campsite.id)
                        .onSuccessTask { winners ->
                            Tasks.forResult(campsite.id to winners)
                        }
                }
                Tasks.whenAllSuccess<Pair<String, List<String>?>>(drawTasks)
            }
            .onSuccessTask { results ->
                val newlyDrawn = results.mapNotNull { (campsiteId, winners) ->
                    winners?.let { campsiteId to it }
                }.toMap()
                Tasks.forResult(newlyDrawn)
            }
    }

    /**
     * Commits the winners, the drawn flag, cleared campsite flags, and one INVITED
     * reservation per winner in the same transaction. The drawn flag prevents duplicate draws
     * when the periodic check runs again or multiple devices check at once.
     */
    private fun drawLotteryWinnersTask(campsiteName: String): Task<List<String>?> {
        if (!isValidDocumentId(campsiteName)) {
            return Tasks.forResult(null)
        }

        return db.runTransaction<List<String>?> { transaction ->
            val lotteryCopies = readLotteryCopies(transaction, campsiteName, primaryOnly = true)
                ?: return@runTransaction null
            val lottery = parseLottery(lotteryCopies.primarySnapshot)
                ?: return@runTransaction null
            if (lottery.drawn) {
                clearCampsiteLotteryFlags(transaction, lotteryCopies.campsiteIds)
                return@runTransaction null
            }
            if (!canDrawWinners(lottery)) {
                return@runTransaction null
            }

            // All reads must happen before the first transaction write.
            val primaryCampsiteId = lotteryCopies.primarySnapshot.reference.parent.parent?.id
                ?: campsiteName
            val ownerId = transaction.get(campsitesCollection.document(primaryCampsiteId))
                .getString("ownerID").orEmpty()

            val winners = lottery.waitlist.distinct()
                .shuffled()
                .take(lottery.numWinners)

            // Update every participating campsite together.
            updateLotteryCopies(
                transaction, lotteryCopies.campsiteIds,
                mapOf("winners" to winners, "drawn" to true)
            )
            clearCampsiteLotteryFlags(transaction, lotteryCopies.campsiteIds)

            for (entrantId in winners) {
                val invitationId = "${lottery.id}_$entrantId"
                val reservationDocument = db.collection("reservations").document(invitationId)
                val invitation = Reservation(
                    id = reservationDocument.id,
                    lotteryId = lottery.id,
                    ownerId = ownerId,
                    entrantId = entrantId,
                    status = ReservationStatus.INVITED
                )
                transaction.set(reservationDocument, invitation)
            }

            winners
        }
    }

    /**
     * A lottery is eligible once its registration period ends. Empty waitlists
     * are marked drawn as well, with no winners or invitations.
     */
    private fun canDrawWinners(lottery: Lottery): Boolean {
        val now = LocalDateTime.now(registrationZone)
        return !lottery.drawn &&
            !now.isBefore(lottery.period.registrationEnd) &&
            lottery.numWinners > 0
    }

    private fun clearCampsiteLotteryFlags(transaction: Transaction, campsiteIds: List<String>) {
        for (campsiteId in campsiteIds) {
            transaction.update(campsitesCollection.document(campsiteId), mapOf("inLottery" to false))
        }
    }

    // Joining and leaving the waitlist

    /**
     * Removes the user from every campsite's waitlist and their saved joined lotteries.
     * Updates the local user only after the transaction commits.
     * Calls [onResult] with false if the user cannot leave or the transaction fails.
     */
    fun leaveLotteryWaitlist(
        campsiteName: String,
        user: User,
        onResult: (Boolean) -> Unit
    ) {
        val entrantId = user.deviceID

        if (!isValidDocumentId(campsiteName) || !isValidDocumentId(entrantId)) {
            onResult(false)
            return
        }

        val userDocument = db.collection("users").document(entrantId)

        db.runTransaction<String?> { transaction ->
            val lotteryCopies = readLotteryCopies(transaction, campsiteName)
                ?: return@runTransaction null

            val lottery = parseLottery(lotteryCopies.primarySnapshot)
                ?: return@runTransaction null

            if (lottery.drawn || entrantId !in lottery.waitlist) {
                return@runTransaction null
            }

            // Read user before performing any writes.
            val userSnapshot = transaction.get(userDocument)
            val updatedWaitlist = lottery.waitlist.filterNot { it == entrantId }
            updateLotteryCopies(
                transaction, lotteryCopies.campsiteIds,
                mapOf("waitlist" to updatedWaitlist)
            )

            // Update the user's joined lotteries in Firestore.
            if (userSnapshot.exists()) {
                val updatedJoinedLotteries = readJoinedLotteries(userSnapshot).filterNot { entry ->
                    (entry as? Map<*, *>)?.get("lotteryId") == lottery.id
                }

                transaction.update(
                    userDocument,
                    "joinedLotteries",
                    updatedJoinedLotteries
                )
            }

            lottery.id
        }.addOnSuccessListener { lotteryId ->
            if (lotteryId != null) {
                user.joinedLotteries = user.joinedLotteries.filterNot {
                    it.lotteryId == lotteryId
                }
            }
            onResult(lotteryId != null)
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
        if (!isValidDocumentId(campsiteName) || !isValidDocumentId(entrantId)) {
            onResult(false)
            return
        }

        db.runTransaction<String?> { transaction ->
            val lotteryCopies = readLotteryCopies(transaction, campsiteName)
                ?: return@runTransaction null
            val lottery = parseLottery(lotteryCopies.primarySnapshot)
                ?: return@runTransaction null
            if (!canJoinWaitlist(lottery, entrantId)) {
                return@runTransaction null
            }

            updateLotteryCopies(
                transaction, lotteryCopies.campsiteIds,
                mapOf("waitlist" to lottery.waitlist + entrantId)
            )

            lottery.id
        }.addOnSuccessListener { lotteryId ->
            if (lotteryId != null) {
                user.joinedLotteries += JoinedLottery(lotteryId, campsiteName)
            }
            onResult(lotteryId != null)
        }.addOnFailureListener {
            onResult(false)
        }
        //
    }

    /**
     * Registration must be open, with space for a user who has not joined or won.
     * A waitlist capacity of zero means unlimited.
     */
    private fun canJoinWaitlist(lottery: Lottery, entrantId: String): Boolean {
        val now = LocalDateTime.now(registrationZone)
        val waitlistHasSpace = lottery.waitlistCapacity <= 0 ||
            lottery.waitlist.size < lottery.waitlistCapacity
        return !lottery.drawn &&
            lottery.period.registrationIsOpen(now) &&
            entrantId !in lottery.waitlist &&
            entrantId !in lottery.winners &&
            waitlistHasSpace
    }

    // Creating and saving

    /**
     * Creates all lottery copies and their shared booking schedule in one transaction.
     * Rechecks ownership, occupancy, availability, and booking conflicts against Firestore.
     * No existing lottery or schedule is replaced when a check or write fails.
     */
    fun createLotteryWithBookingSchedule(
        user: User,
        lotteryId: String,
        campsiteIds: List<String>,
        period: Registration,
        numWinners: Int,
        waitlistCapacity: Int,
        bookingStart: LocalDateTime,
        bookingEnd: LocalDateTime
    ): Task<BookingSchedule<Lottery>> {
        val selectedCampsiteIds = campsiteIds.toList()
        val userId = user.deviceID
        val isAdmin = user.userType.equals("admin", ignoreCase = true)
        val isOwner = user.userType.equals("campsite owner", ignoreCase = true)
        val requestedLottery = Lottery(
            id = lotteryId,
            campsiteIds = selectedCampsiteIds,
            period = Registration(period.registrationStart, period.registrationEnd),
            numWinners = numWinners,
            waitlistCapacity = waitlistCapacity
        )
        val midnight = bookingStart.toLocalDate().atStartOfDay()
        val firstNightStart = if (midnight.isBefore(bookingStart)) midnight.plusDays(1) else midnight
        val validationError = when {
            !isValidDocumentId(userId) || (!isAdmin && !isOwner) ->
                "Only an administrator or campsite owner can create a lottery."
            !isValidDocumentId(lotteryId) -> "Invalid lottery ID."
            selectedCampsiteIds.isEmpty() || selectedCampsiteIds.any { !isValidDocumentId(it) } ||
                selectedCampsiteIds.distinct().size != selectedCampsiteIds.size ->
                "Select unique, valid campsites."
            !bookingEnd.isAfter(bookingStart) -> "Booking must end after it starts."
            bookingStart.isBefore(requestedLottery.period.registrationEnd) ->
                "Booking cannot start before registration closes."
            firstNightStart.plusDays(1).isAfter(bookingEnd) ->
                "Choose a booking period that includes at least one full night, from midnight to midnight."
            else -> lotteryValidationError(requestedLottery)
        }
        if (validationError != null) {
            return Tasks.forException(IllegalArgumentException(validationError))
        }

        val schedulesCollection = db.collection("bookingSchedules")
        val scheduleDocument = schedulesCollection.document(lotteryId)
        return db.runTransaction<BookingSchedule<Lottery>> { transaction ->
            // Read campsite parents first. Concurrent creations change these documents,
            // which makes Firestore retry before committing a conflicting creation.
            val campsiteSnapshots = selectedCampsiteIds.associateWith { campsiteId ->
                transaction.get(campsitesCollection.document(campsiteId))
            }
            val existingLotterySnapshots = selectedCampsiteIds.map { campsiteId ->
                transaction.get(lotteryDocument(campsiteId))
            }
            check(!transaction.get(scheduleDocument).exists()) {
                "A booking schedule already exists for this lottery."
            }
            // A start reached while filling in the form means registration opens on creation.
            val registrationStart = maxOf(
                requestedLottery.period.registrationStart,
                LocalDateTime.now(registrationZone)
            )
            check(requestedLottery.period.registrationEnd.isAfter(registrationStart)) {
                "Registration must end in the future."
            }
            for ((campsiteId, campsiteSnapshot) in campsiteSnapshots) {
                check(campsiteSnapshot.exists()) { "Campsite $campsiteId no longer exists." }
                check(isAdmin || campsiteSnapshot.getString("ownerID") == userId) {
                    "You must own every selected campsite to create this lottery."
                }
                check(campsiteSnapshot.getBoolean("inLottery") != true) {
                    "Campsite $campsiteId is already in a lottery."
                }
                val occupancy = campsiteSnapshot.getLong("occupancy")
                check(occupancy != null && occupancy >= numWinners) {
                    "Campsite $campsiteId must have room for at least $numWinners winners."
                }
            }
            check(existingLotterySnapshots.none { it.exists() }) {
                "A selected campsite already has a lottery. Reload the campsite list."
            }

            // The callback runs on Firestore's transaction executor, not the UI thread.
            // Discover existing schedules, then read each through the transaction so
            // concurrent changes to those schedules also invalidate this transaction.
            val scheduleQueries = selectedCampsiteIds.chunked(10).map { campsiteGroup ->
                schedulesCollection.whereArrayContainsAny("campsiteIds", campsiteGroup).get(Source.SERVER)
            }
            val queryResults = Tasks.await(
                Tasks.whenAllSuccess<QuerySnapshot>(scheduleQueries), 15, TimeUnit.SECONDS
            )
            val existingSchedules = queryResults.flatMap { it.documents }.distinctBy { it.id }
            for (scheduleSnapshot in existingSchedules) {
                val savedSchedule = checkNotNull(
                    BookingScheduleRepository.fromSnapshot(transaction.get(scheduleSnapshot.reference))
                ) { "An existing booking schedule could not be read. Please try again." }
                check(savedSchedule.bookingEnd.isAfter(savedSchedule.bookingStart)) {
                    "An existing booking schedule has invalid dates. Please correct it first."
                }
                val overlaps = bookingStart.isBefore(savedSchedule.bookingEnd) &&
                    bookingEnd.isAfter(savedSchedule.bookingStart)
                check(!overlaps) { "The booking period overlaps an existing schedule for a selected campsite." }
            }

            val primaryCampsite = checkNotNull(campsiteSnapshots[selectedCampsiteIds.first()])
            val ownerId = primaryCampsite.getString("ownerID").orEmpty()
            check(isValidDocumentId(ownerId)) { "The primary campsite needs a valid owner." }
            val lottery = requestedLottery.copy(
                ownerId = ownerId,
                period = Registration(registrationStart, requestedLottery.period.registrationEnd)
            )
            val schedule = BookingSchedule(lottery, bookingStart, bookingEnd)
            val lotteryData = lotteryFields(lottery, selectedCampsiteIds)

            // Every read and validation has finished. These writes commit or fail together.
            for (campsiteId in selectedCampsiteIds) {
                transaction.set(
                    lotteryDocument(campsiteId),
                    lotteryData + ("isPrimary" to (campsiteId == selectedCampsiteIds.first()))
                )
                transaction.update(campsitesCollection.document(campsiteId), mapOf("inLottery" to true))
            }
            transaction.set(scheduleDocument, BookingScheduleRepository.toMap(schedule))
            schedule
        }
    }

    /**
     * Creates a lottery after checking that all campsite names exist in [campsites].
     * Saves every copy and marks each campsite as in a lottery before returning it.
     * [campsiteName] is the primary campsite; a [waitlistCapacity] of zero is unlimited.
     */
    fun createLottery(
        campsiteName: String,
        period: Registration,
        numWinners: Int,
        campsites: Map<String, Campsite>,
        waitlistCapacity: Int = 0,
        additionalCampsiteIds: List<String> = emptyList()
    ): Task<Lottery> {
        // Keep the primary campsite first when removing blanks and duplicates.
        val campsiteIds = (listOf(campsiteName) + additionalCampsiteIds)
            .filter { it.isNotBlank() }.distinct()

        for (campsiteId in campsiteIds) {
            if (!campsites.containsKey(campsiteId)) {
                return Tasks.forException(IllegalArgumentException("Campsite $campsiteId does not exist."))
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
     * Saves a configured lottery to every campsite, rejecting invalid settings
     * or changes that would overwrite newer waitlist or draw results.
     */
    fun saveLottery(campsiteName: String, lottery: Lottery): Task<Void> =
        saveLottery(campsiteName, lottery, createOnly = false)

    /**
     * Validates and saves all copies atomically, marking their campsites as active.
     * With [createOnly], no campsite may already have a lottery and the primary
     * campsite must have an occupancy set.
     */
    private fun saveLottery(campsiteName: String, lottery: Lottery, createOnly: Boolean): Task<Void> {
        val campsiteIds = participatingCampsiteIds(campsiteName, lottery.campsiteIds)
        if (!isValidDocumentId(campsiteName) || campsiteIds.any { !isValidDocumentId(it) }) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }

        val validationError = lotteryValidationError(lottery)
        if (validationError != null) {
            return Tasks.forException(IllegalArgumentException(validationError))
        }

        val primaryCampsiteId = campsiteIds.first()
        val lotteryData = lotteryFields(lottery, campsiteIds)

        return db.runTransaction<Void> { transaction ->
            // Firestore requires all reads to happen before any writes.
            checkCampsiteOccupancy(transaction, primaryCampsiteId, lottery.numWinners, requireOccupancy = createOnly)
            checkSavedLotteryCopies(transaction, campsiteIds, lottery, createOnly)

            for (campsiteId in campsiteIds) {
                transaction.set(
                    lotteryDocument(campsiteId),
                    lotteryData + ("isPrimary" to (campsiteId == primaryCampsiteId)),
                    SetOptions.merge()
                )
                transaction.update(campsitesCollection.document(campsiteId), mapOf("inLottery" to true))
            }
            null
        }
    }

    /** Returns the first invalid setting, or null when the lottery can be saved. */
    private fun lotteryValidationError(lottery: Lottery): String? {
        if (lottery.id.isBlank() || lottery.numWinners < 1 || lottery.waitlistCapacity < 0) {
            return "Set a lottery ID, at least one winner, and a nonnegative waitlist capacity."
        }
        if (!lottery.period.registrationStart.isBefore(lottery.period.registrationEnd)) {
            return "Registration end must be after its start."
        }
        return null
    }

    /**
     * Firestore fields shared by every copy. Dates use ISO text, with an additional
     * closing timestamp for sorting and filtering.
     */
    private fun lotteryFields(lottery: Lottery, campsiteIds: List<String>): Map<String, Any> {
        val closingTime = lottery.period.registrationEnd.atZone(registrationZone).toInstant()
        return mapOf(
            "id" to lottery.id,
            "ownerId" to lottery.ownerId,
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
     * Checks that occupancy supports the draw, before any transaction writes.
     * Missing occupancy is allowed only when [requireOccupancy] is false.
     */
    private fun checkCampsiteOccupancy(
        transaction: Transaction,
        campsiteId: String,
        numWinners: Int,
        requireOccupancy: Boolean
    ) {
        val occupancy = transaction.get(campsitesCollection.document(campsiteId)).getLong("occupancy")
        check(!requireOccupancy || occupancy != null) {
            "Set the primary campsite's occupancy before creating a lottery."
        }
        check(occupancy == null || numWinners <= occupancy) {
            "Number of winners exceeds the primary campsite's occupancy."
        }
    }

    /**
     * Rejects conflicting lottery IDs, omitted campsites, lost entrants, or changed
     * draw results. Reads all existing copies before the caller performs any writes.
     */
    private fun checkSavedLotteryCopies(
        transaction: Transaction,
        campsiteIds: List<String>,
        lottery: Lottery,
        createOnly: Boolean
    ) {
        val newWaitlist = lottery.waitlist.toList()
        val newWinners = lottery.winners.toList()

        for (campsiteId in campsiteIds) {
            val savedSnapshot = transaction.get(lotteryDocument(campsiteId))
            if (!savedSnapshot.exists()) continue

            check(!createOnly && savedSnapshot.getString("id") == lottery.id) {
                "$campsiteId already has a lottery."
            }

            val savedCampsiteIds = participatingCampsiteIds(campsiteId, readStringList(savedSnapshot, "campsiteIds"))
            check(campsiteIds.containsAll(savedCampsiteIds)) {
                "The save must include every existing campsite."
            }

            check(newWaitlist.containsAll(readStringList(savedSnapshot, "waitlist"))) {
                "The waitlist changed. Reload the lottery before saving."
            }

            if (savedSnapshot.getBoolean("drawn") == true) {
                val sameWinners = newWinners == readStringList(savedSnapshot, "winners")
                check(lottery.drawn && sameWinners) {
                    "The lottery has been drawn. Reload it before saving."
                }
            }
        }
    }

    // Focused lottery updates

    /**
     * Changes registration dates on all copies. The caller must be an admin or own
     * every campsite, and the lottery must not have been drawn.
     */
    fun updateLotteryPeriod(
        user: User,
        campsiteName: String,
        period: Registration
    ): Task<Void> {
        // Registration is mutable; capture the values passed to this call.
        val registrationStart = period.registrationStart
        val registrationEnd = period.registrationEnd
        if (!registrationStart.isBefore(registrationEnd)) {
            return Tasks.forException(IllegalArgumentException("Registration end must be after its start."))
        }

        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            val changes = mutableMapOf<String, Any>()
            if (savedLottery.period.registrationStart != registrationStart) {
                changes["registrationStart"] = registrationStart.toString()
            }
            if (savedLottery.period.registrationEnd != registrationEnd) {
                val closingTime = registrationEnd.atZone(registrationZone).toInstant()
                changes["registrationEnd"] = registrationEnd.toString()
                changes["registrationEndAt"] = Timestamp(closingTime.epochSecond, closingTime.nano)
            }
            updateLotteryCopies(transaction, lotteryCopies.campsiteIds, changes)
        }
    }

    /**
     * Changes the waitlist limit, subject to the same permissions as [updateLotteryPeriod].
     * Zero means unlimited; a positive limit must fit the existing waitlist.
     */
    fun updateLotteryWaitlistCapacity(
        user: User,
        campsiteName: String,
        waitlistCapacity: Int
    ): Task<Void> {
        if (waitlistCapacity < 0) {
            return Tasks.forException(IllegalArgumentException("Waitlist capacity cannot be negative."))
        }
        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            check(waitlistCapacity == 0 || waitlistCapacity >= savedLottery.waitlist.size) {
                "The new capacity cannot be less than the current waitlist size."
            }
            if (waitlistCapacity != savedLottery.waitlistCapacity) {
                updateLotteryCopies(
                    transaction, lotteryCopies.campsiteIds,
                    mapOf("waitlistCapacity" to waitlistCapacity)
                )
            }
        }
    }

    /**
     * Changes the winner count, subject to the same permissions as [updateLotteryPeriod].
     * The count must be positive and fit every participating campsite's occupancy.
     */
    fun updateLotteryNumWinners(
        user: User,
        campsiteName: String,
        numWinners: Int
    ): Task<Void> {
        if (numWinners < 1) {
            return Tasks.forException(IllegalArgumentException("Number of winners must be at least 1."))
        }
        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            // Reads happen before any updates, as required by Firestore transactions.
            for (campsiteId in lotteryCopies.campsiteIds) {
                checkCampsiteOccupancy(transaction, campsiteId, numWinners, requireOccupancy = true)
            }
            if (numWinners != savedLottery.numWinners) {
                updateLotteryCopies(transaction, lotteryCopies.campsiteIds, mapOf("numWinners" to numWinners))
            }
        }
    }

    /**
     * Replaces the waitlist and synchronizes affected users' joined lotteries atomically.
     * The caller must be an admin or own every campsite. Entrants should use join/leave.
     * Rejects duplicate or invalid IDs, ineligible users, winners, and capacity overflow.
     */
    fun updateLotteryWaitlist(
        user: User,
        campsiteName: String,
        waitlist: List<String>
    ): Task<Void> {
        if (waitlist.any { !isValidDocumentId(it) } || waitlist.size != waitlist.distinct().size) {
            return Tasks.forException(
                IllegalArgumentException("Waitlist must contain unique, valid user IDs.")
            )
        }
        val updatedWaitlist = waitlist.toList()
        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            check(savedLottery.waitlistCapacity == 0 || updatedWaitlist.size <= savedLottery.waitlistCapacity) {
                "The waitlist exceeds its capacity."
            }
            check(updatedWaitlist.none { it in savedLottery.winners }) {
                "A winner cannot also be on the waitlist."
            }

            val userChanges = prepareWaitlistUserChanges(transaction, savedLottery, updatedWaitlist, campsiteName)
            // All user reads and checks are complete before writing any document.
            if (updatedWaitlist != savedLottery.waitlist) {
                updateLotteryCopies(transaction, lotteryCopies.campsiteIds, mapOf("waitlist" to updatedWaitlist))
            }
            for ((entrantId, joinedLotteries) in userChanges) {
                transaction.update(db.collection("users").document(entrantId), "joinedLotteries", joinedLotteries)
            }
        }
    }

    /** Reads and validates affected users, then prepares their joined lotteries without writing. */
    private fun prepareWaitlistUserChanges(
        transaction: Transaction,
        savedLottery: Lottery,
        updatedWaitlist: List<String>,
        campsiteName: String
    ): Map<String, List<Any>> {
        val addedEntrantIds = updatedWaitlist.filterNot { it in savedLottery.waitlist }.toSet()
        val removedEntrantIds = savedLottery.waitlist.filterNot { it in updatedWaitlist }
        val affectedUsers = (addedEntrantIds + removedEntrantIds).associateWith { entrantId ->
            transaction.get(db.collection("users").document(entrantId))
        }
        val userChanges = mutableMapOf<String, List<Any>>()
        for ((entrantId, userSnapshot) in affectedUsers) {
            if (entrantId in addedEntrantIds) {
                check(userSnapshot.exists()) { "User $entrantId does not exist." }
                val userType = userSnapshot.getString("userType").orEmpty().lowercase()
                check(userType == "entrant" || userType == "admin") {
                    "Only entrants and admins may join the waitlist."
                }
            }
            // A removed entrant may no longer have an account.
            if (!userSnapshot.exists()) continue

            val joinedLotteries = readJoinedLotteries(userSnapshot)
            val withoutThisLottery = joinedLotteries.filterNot { entry ->
                (entry as? Map<*, *>)?.get("lotteryId") == savedLottery.id
            }
            val updatedJoinedLotteries = if (entrantId in addedEntrantIds) {
                withoutThisLottery + mapOf("lotteryId" to savedLottery.id, "campsiteName" to campsiteName)
            } else {
                withoutThisLottery
            }
            if (updatedJoinedLotteries != joinedLotteries) {
                userChanges[entrantId] = updatedJoinedLotteries
            }
        }
        return userChanges
    }

    /**
     * Adds an unused campsite with enough occupancy for the draw.
     * The caller must be an admin or own every existing campsite and the new one.
     * Drawn lotteries cannot be changed.
     */
    fun addCampsiteToLottery(
        user: User,
        campsiteName: String,
        newCampsiteName: String
    ): Task<Void> {
        if (!isValidDocumentId(newCampsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid new campsite ID."))
        }
        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            check(newCampsiteName !in lotteryCopies.campsiteIds) {
                "This campsite is already part of the lottery."
            }

            val newCampsiteDocument = campsitesCollection.document(newCampsiteName)
            val newCampsiteSnapshot = transaction.get(newCampsiteDocument)
            val existingLotterySnapshot = transaction.get(lotteryDocument(newCampsiteName))
            check(newCampsiteSnapshot.exists()) { "The new campsite does not exist." }
            check(newCampsiteSnapshot.getBoolean("inLottery") != true && !existingLotterySnapshot.exists()) {
                "The new campsite is already assigned to a lottery."
            }
            check(canEditCampsite(user, newCampsiteSnapshot)) {
                "Only an admin or the new campsite's owner can add it."
            }
            val occupancy = newCampsiteSnapshot.getLong("occupancy")
            check(occupancy != null && occupancy >= savedLottery.numWinners) {
                "The new campsite occupancy must be at least ${savedLottery.numWinners}."
            }

            val updatedCampsiteIds = lotteryCopies.campsiteIds + newCampsiteName
            updateLotteryCopies(transaction, lotteryCopies.campsiteIds, mapOf("campsiteIds" to updatedCampsiteIds))
            transaction.set(
                lotteryDocument(newCampsiteName),
                lotteryFields(savedLottery, updatedCampsiteIds) + ("isPrimary" to false)
            )
            transaction.update(newCampsiteDocument, mapOf("inLottery" to true))
        }
    }

    /**
     * Removes a campsite and points affected user entries to the remaining primary copy.
     * Requires an undrawn lottery and an admin or owner of every participating campsite.
     * To remove the last campsite, use [deleteLottery] instead.
     */
    fun removeCampsiteFromLottery(
        user: User,
        campsiteName: String,
        removeCampsiteName: String
    ): Task<Void> {
        if (!isValidDocumentId(removeCampsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID to remove."))
        }
        return editLottery(user, campsiteName) { transaction, lotteryCopies, savedLottery ->
            check(removeCampsiteName in lotteryCopies.campsiteIds) {
                "The campsite is not in this lottery."
            }
            check(lotteryCopies.campsiteIds.size > 1) {
                "Cannot remove the last campsite. Use deleteLottery instead."
            }
            val remainingCampsiteIds = lotteryCopies.campsiteIds.filterNot { it == removeCampsiteName }
            val newPrimaryCampsiteId = remainingCampsiteIds.first()
            // The new primary must support the promised number of winners.
            if (removeCampsiteName == lotteryCopies.campsiteIds.first()) {
                checkCampsiteOccupancy(transaction, newPrimaryCampsiteId, savedLottery.numWinners, requireOccupancy = true)
            }

            // Entries made through the removed campsite should point to one that remains.
            // Read every affected user before any writes to keep join references consistent.
            val userChanges = mutableMapOf<String, List<Any>>()
            for (entrantId in savedLottery.waitlist.distinct()) {
                check(isValidDocumentId(entrantId)) { "An invalid entrant ID exists on the waitlist." }
                val userSnapshot = transaction.get(db.collection("users").document(entrantId))
                if (!userSnapshot.exists()) continue
                val joinedLotteries = readJoinedLotteries(userSnapshot)
                val updatedJoinedLotteries = joinedLotteries.map { entry ->
                    val joinedLottery = entry as? Map<*, *>
                    if (joinedLottery?.get("lotteryId") == savedLottery.id &&
                        joinedLottery["campsiteName"] == removeCampsiteName
                    ) {
                        mapOf("lotteryId" to savedLottery.id, "campsiteName" to newPrimaryCampsiteId)
                    } else {
                        entry
                    }
                }.distinct()
                if (joinedLotteries != updatedJoinedLotteries) userChanges[entrantId] = updatedJoinedLotteries
            }

            // All Firestore reads are finished. Keep every remaining copy in sync.
            for (campsiteId in remainingCampsiteIds) {
                transaction.update(
                    lotteryDocument(campsiteId),
                    mapOf(
                        "campsiteIds" to remainingCampsiteIds,
                        "isPrimary" to (campsiteId == newPrimaryCampsiteId)
                    )
                )
            }
            transaction.delete(lotteryDocument(removeCampsiteName))
            transaction.update(campsitesCollection.document(removeCampsiteName), mapOf("inLottery" to false))
            for ((entrantId, joinedLotteries) in userChanges) {
                transaction.update(db.collection("users").document(entrantId), "joinedLotteries", joinedLotteries)
            }
        }
    }

    /**
     * Checks matching lottery IDs, ownership of every campsite (unless admin),
     * and that the lottery is undrawn before invoking [edit].
     * The edit must finish all its reads before performing any writes.
     */
    private fun editLottery(
        user: User,
        campsiteName: String,
        edit: (Transaction, LotteryCopies, Lottery) -> Unit
    ): Task<Void> {
        if (!isValidDocumentId(campsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }
        return db.runTransaction<Void> { transaction ->
            val lotteryCopies = checkNotNull(readLotteryCopies(transaction, campsiteName)) {
                "The lottery was not found or its copies are inconsistent."
            }
            val savedLottery = checkNotNull(parseLottery(lotteryCopies.primarySnapshot)) {
                "The lottery was not found."
            }
            // Read all owner records before invoking an edit that could perform writes.
            val campsiteSnapshots = lotteryCopies.campsiteIds.map { campsiteId ->
                transaction.get(campsitesCollection.document(campsiteId))
            }
            check(campsiteSnapshots.all { it.exists() && canEditCampsite(user, it) }) {
                "Only an admin or an owner of every participating campsite can edit this lottery."
            }
            check(!savedLottery.drawn) { "The lottery has been drawn and cannot be edited." }
            edit(transaction, lotteryCopies, savedLottery)
            null
        }
    }

    /** Admins can edit any campsite; other users must match its owner ID. */
    private fun canEditCampsite(user: User, campsite: DocumentSnapshot): Boolean =
        user.userType.equals("admin", ignoreCase = true) ||
            (user.deviceID.isNotBlank() && campsite.getString("ownerID") == user.deviceID)

    /** Applies nonempty changes to every participating campsite's lottery document. */
    private fun updateLotteryCopies(
        transaction: Transaction,
        campsiteIds: List<String>,
        changes: Map<String, Any>
    ) {
        if (changes.isEmpty()) return
        for (campsiteId in campsiteIds) {
            transaction.update(lotteryDocument(campsiteId), changes)
        }
    }

    // Loading and deleting

    /**
     * Loads the current lottery, returning null for invalid IDs, missing or malformed
     * documents, and failed lookups.
     */
    fun getLottery(campsiteName: String, onResult: (Lottery?) -> Unit) {
        if (!isValidDocumentId(campsiteName)) {
            onResult(null)
            return
        }

        lotteryDocument(campsiteName).get().addOnSuccessListener { lotterySnapshot ->
            val lottery = try {
                parseLottery(lotterySnapshot)
            } catch (_: Exception) {
                null
            }
            onResult(lottery)
        }.addOnFailureListener {
            onResult(null)
        }
    }

    /**
     * Deletes all copies and clears their campsite flags in one transaction.
     * Fails if the campsite ID is invalid or the lottery copies are missing or inconsistent.
     */
    fun deleteLottery(campsiteName: String): Task<Void> {
        if (!isValidDocumentId(campsiteName)) {
            return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        }

        return db.runTransaction<Void> { transaction ->
            val lotteryCopies = checkNotNull(readLotteryCopies(transaction, campsiteName)) {
                "The lottery or one of its campsite copies is missing or inconsistent."
            }
            for (campsiteId in lotteryCopies.campsiteIds) {
                transaction.delete(lotteryDocument(campsiteId))
                transaction.update(campsitesCollection.document(campsiteId), mapOf("inLottery" to false))
            }
            null
        }
    }

    // Reading from Firestore

    /**
     * Reads the primary copy and verifies that every copy has the same lottery ID.
     * Returns null for missing or inconsistent copies. With [primaryOnly], skips
     * requests made through a secondary campsite, so periodic draws run just once.
     */
    private fun readLotteryCopies(
        transaction: Transaction,
        campsiteName: String,
        primaryOnly: Boolean = false
    ): LotteryCopies? {
        val selectedSnapshot = transaction.get(lotteryDocument(campsiteName))
        val lotteryId = selectedSnapshot.getString("id")
        if (lotteryId.isNullOrBlank()) return null

        // The first nonblank campsite ID identifies the primary copy.
        val storedCampsiteIds = readStringList(selectedSnapshot, "campsiteIds")
        val primaryCampsiteId = storedCampsiteIds.firstOrNull { it.isNotBlank() } ?: campsiteName
        if (!isValidDocumentId(primaryCampsiteId)) return null
        if (primaryOnly && primaryCampsiteId != campsiteName) return null

        val primarySnapshot = if (primaryCampsiteId == campsiteName) {
            selectedSnapshot
        } else {
            transaction.get(lotteryDocument(primaryCampsiteId))
        }
        if (primarySnapshot.getString("id") != lotteryId) return null

        val campsiteIds = participatingCampsiteIds(
            campsiteName, readStringList(primarySnapshot, "campsiteIds") + primaryCampsiteId
        )
        if (campsiteIds.any { !isValidDocumentId(it) }) return null

        for (campsiteId in campsiteIds) {
            val copySnapshot = when (campsiteId) {
                campsiteName -> selectedSnapshot
                primaryCampsiteId -> primarySnapshot
                else -> transaction.get(lotteryDocument(campsiteId))
            }
            if (copySnapshot.getString("id") != lotteryId) return null
        }

        return LotteryCopies(primarySnapshot, campsiteIds)
    }

    /**
     * Parses a lottery, or returns null for a missing document. Absent optional fields
     * default to one winner, unlimited capacity, empty lists, and an undrawn lottery.
     * Missing or malformed registration dates throw an exception.
     */
    private fun parseLottery(snapshot: DocumentSnapshot): Lottery? {
        if (!snapshot.exists()) return null

        val period = Registration(
            registrationStart = readRegistrationDate(snapshot, "registrationStart"),
            registrationEnd = readRegistrationDate(snapshot, "registrationEnd")
        )
        return Lottery(
            id = snapshot.getString("id").orEmpty(),
            ownerId = snapshot.getString("ownerId").orEmpty(),
            campsiteIds = readStringList(snapshot, "campsiteIds"),
            period = period,
            numWinners = snapshot.getLong("numWinners")?.toInt() ?: 1,
            waitlistCapacity = snapshot.getLong("waitlistCapacity")?.toInt() ?: 0,
            waitlist = readStringList(snapshot, "waitlist"),
            winners = readStringList(snapshot, "winners"),
            drawn = snapshot.getBoolean("drawn") ?: false
        )
    }

    /** Reads a required ISO date, throwing if it is missing or malformed. */
    private fun readRegistrationDate(snapshot: DocumentSnapshot, field: String): LocalDateTime {
        val dateText = requireNotNull(snapshot.getString(field)) { "Missing $field" }
        return LocalDateTime.parse(dateText)
    }

    /** Reads string values only; missing fields or fields of other types become empty lists. */
    private fun readStringList(snapshot: DocumentSnapshot, field: String): List<String> {
        val values = snapshot.get(field) as? List<*> ?: return emptyList()
        return values.filterIsInstance<String>()
    }

    /** Keeps unknown legacy entries intact when updating a user's joined lotteries. */
    private fun readJoinedLotteries(userSnapshot: DocumentSnapshot): List<Any> =
        (userSnapshot.get("joinedLotteries") as? List<*>).orEmpty().filterNotNull()

    // Small helpers

    /** Includes the selected campsite, preserving stored order while removing blanks and duplicates. */
    private fun participatingCampsiteIds(campsiteName: String, campsiteIds: List<String>): List<String> =
        (campsiteIds + campsiteName).filter { it.isNotBlank() }.distinct()

    /** Repository IDs must contain a nonwhitespace character and cannot include a path separator. */
    private fun isValidDocumentId(value: String): Boolean = value.isNotBlank() && '/' !in value
}
