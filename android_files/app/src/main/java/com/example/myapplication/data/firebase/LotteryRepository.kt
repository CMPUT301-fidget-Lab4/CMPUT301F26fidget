package com.example.myapplication.data.firebase

import com.example.myapplication.core.datetime.AlbertaTime

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.ListenerRegistration
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.booking.BookingSchedule.Booking
import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.lottery.BookingScheduleReference
import com.example.myapplication.features.lottery.JoinWaitlistResult
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.LotteryDrawOutcome
import com.example.myapplication.features.lottery.Registration
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.firestore
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The primary lottery document and the IDs of all campsites sharing it.
 * The first campsite in the stored list holds the primary copy.
 */
private class LotteryCopies(
    val primarySnapshot: DocumentSnapshot,
    val campsiteIds: List<String>
)

private data class ListingDetails(
    val registrationStart: LocalDateTime,
    val registrationEnd: LocalDateTime,
    val numWinners: Int,
    val waitlistCapacity: Int
)

/**
 * Reads and writes lotteries in Firestore.
 *
 * Lotteries are stored at campsites/{campsiteName}/lottery/current. A lottery that
 * covers several campsites is stored once per campsite, and every copy is kept in sync
 * by doing all changes inside Firestore transactions.
 * Completed copies move to lottery/history_<lotteryId> when current is replaced.
 * The collection-group listener loads both current and historical records.
 *
 * @property db the Firestore instance to use
 */
class LotteryRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val campsiteLookup: (String) -> Campsite? = { null },
    private val userLookup: (String) -> User? = { null },
    private val clock: Clock = Clock.system(AlbertaTime.zone),
    private val drawRandom: Random = Random.Default
) {
    private val campsitesCollection = db.collection("campsites")

    private val _allLotteries = mutableStateMapOf<String, Lottery>()
    val allLotteries: Map<String, Lottery> get() = java.util.Collections.unmodifiableMap(_allLotteries)
    var lotteriesLoaded by mutableStateOf(false)
        private set
    var listenerError: Exception? by mutableStateOf(null)
        private set
    private var lotteryListener: ListenerRegistration? = null
    private val collectionListeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    private var currentCampsitesByLottery = emptyMap<String, List<String>>()

    /** Listings managed by this campsite owner, separate from personal joined membership. */
    fun getOwnedLotteries(user: User?, campsites: Map<String, Campsite>): List<Lottery> {
        if (user?.userType?.trim()?.equals("campsite owner", true) != true) return emptyList()
        return allLotteries.values.filter { canViewLotteryWaitlist(user, it, campsites) }
            .distinctBy { it.id }.sortedByDescending { it.period.registrationStart }
    }

    /** Reuses the Firestore listener for derived campsite collections, even without a Compose screen. */
    internal fun observeLotteries(onChange: () -> Unit): ListenerRegistration {
        collectionListeners.add(onChange)
        return ListenerRegistration { collectionListeners.remove(onChange) }
    }

    // Citation from ChatGpt

    fun startListening() {
        if (lotteryListener != null) return
        lotteryListener = db.collectionGroup("lottery").addSnapshotListener { snapshot, error ->
            if (error != null) {
                listenerError = error
                close()
                collectionListeners.forEach { it() }
                return@addSnapshotListener
            }
            if (snapshot == null) { listenerError = IllegalStateException("No lottery snapshot returned."); return@addSnapshotListener }
            try {
                var parseError: Exception? = null
                currentCampsitesByLottery = snapshot.documents.mapNotNull { document ->
                    if (document.id != "current") return@mapNotNull null
                    val lotteryId = document.getString("id") ?: return@mapNotNull null
                    val campsiteId = document.reference.parent.parent?.id ?: return@mapNotNull null
                    lotteryId to campsiteId
                }.groupBy({ it.first }, { it.second })
                val documents = snapshot.documents.sortedByDescending { it.getBoolean("isPrimary") == true }
                    .distinctBy { it.getString("id") }
                val knownIds = documents.mapNotNull { it.getString("id") }.toSet()
                val loaded = documents.mapNotNull { document ->
                    try {
                        parseLottery(document)?.also { check(it.id.isNotBlank()) { "Lottery ID is missing." } }
                    } catch (exception: Exception) { parseError = exception; null }
                }.associateBy { it.id }
                // A malformed record must not hide unrelated lotteries or erase its last valid value.
                _allLotteries.keys.retainAll(knownIds)
                _allLotteries.putAll(loaded)
                lotteriesLoaded = true
                listenerError = parseError
                collectionListeners.forEach { it() }
            } catch (exception: Exception) { listenerError = exception }
        }
    }

    /** Called when canonical campsite/user listeners change, never as a write-success shortcut. */
    fun refreshReferences() {
        var changed = false
        _allLotteries.toMap().forEach { (id, lottery) ->
            val campsites = lottery.campsites.keys.associateWith(campsiteLookup)
            val entrants = lottery.waitlist.keys.associateWith(userLookup)
            if (campsites != lottery.campsites || entrants != lottery.waitlist) {
                _allLotteries[id] = lottery.copy(campsites = campsites, waitlist = entrants)
                changed = true
            }
        }
        if (changed) collectionListeners.forEach { it() }
    }

    fun close() { lotteryListener?.remove(); lotteryListener = null }

    /** The time zone used for registration dates and times. */
    private val registrationZone = AlbertaTime.zone

    private fun lotteryDocument(campsiteName: String) =
        campsitesCollection.document(campsiteName).collection("lottery").document("current")

    private fun historicalLotteryDocument(campsiteName: String, lotteryId: String) =
        campsitesCollection.document(campsiteName).collection("lottery").document("history_$lotteryId")

    // Drawing winners

    /** A null delay means there is no current work. Zero means a draw or flag repair is due. */
    internal fun timeUntilNextDrawMillis(activeCampsiteIds: Set<String>): Long? {
        val now = LocalDateTime.now(clock)
        return currentCampsitesByLottery.mapNotNull { (lotteryId, campsiteIds) ->
            val lottery = _allLotteries[lotteryId] ?: return@mapNotNull null
            drawCandidateDelayMillis(lottery, campsiteIds.any { it in activeCampsiteIds }, now)
        }.minOrNull()
    }

    /** Finds due candidates in the shared projection, then rechecks every decision at commit. */
    internal fun drawClosedLotteries(activeCampsiteIds: Set<String>): Task<List<LotteryDrawOutcome>> {
        val now = LocalDateTime.now(clock)
        val draws = currentCampsitesByLottery.toSortedMap().mapNotNull { (lotteryId, campsiteIds) ->
            val lottery = _allLotteries[lotteryId] ?: return@mapNotNull null
            val delay = drawCandidateDelayMillis(lottery, campsiteIds.any { it in activeCampsiteIds }, now)
            if (delay != 0L) return@mapNotNull null
            val campsiteId = lottery.campsiteIds.firstOrNull()?.takeIf { it in campsiteIds }
                ?: campsiteIds.first()
            lotteryId to drawLottery(lotteryId, campsiteId)
        }
        if (draws.isEmpty()) return Tasks.forResult(emptyList())

        val executor = Executor { it.run() }
        return Tasks.whenAllComplete(executor, draws.map { it.second }).continueWith(executor) {
            draws.map { (lotteryId, draw) ->
                if (draw.isSuccessful) draw.result else LotteryDrawOutcome.Failed(
                    lotteryId, draw.exception ?: IllegalStateException("Lottery transaction did not complete.")
                )
            }
        }
    }

    internal fun drawLottery(lotteryId: String, campsiteId: String): Task<LotteryDrawOutcome> =
        db.runTransaction<LotteryDrawOutcome> { transaction ->
            val skipped = LotteryDrawOutcome.Skipped(lotteryId)
            val copies = readLotteryCopies(transaction, campsiteId, expectedLotteryId = lotteryId)
                ?: return@runTransaction skipped
            val lottery = parseLottery(copies.primarySnapshot) ?: return@runTransaction skipped
            if (!lottery.drawn && !isDrawDue(lottery, LocalDateTime.now(clock))) return@runTransaction skipped

            val primaryCampsiteId = copies.primarySnapshot.reference.parent.parent?.id ?: campsiteId
            val campsites = copies.campsiteIds.associateWith { id ->
                transaction.get(campsitesCollection.document(id))
            }
            val activeCampsites = campsites.values.filter { it.getBoolean("inLottery") == true }
            if (activeCampsites.isEmpty()) return@runTransaction skipped

            // A previous draw may have left active flags. Only repair copies verified above.
            if (lottery.drawn) {
                activeCampsites.forEach { transaction.update(it.reference, "inLottery", false) }
                return@runTransaction LotteryDrawOutcome.Repaired(lotteryId)
            }

            check(isValidDocumentId(lottery.id)) { "The lottery has an invalid ID." }
            check(campsites.values.all { it.exists() }) { "A participating campsite no longer exists." }
            val ownerId = campsites.getValue(primaryCampsiteId).getString("ownerID").orEmpty()
            val entrantIds = lottery.waitlistIds.filter(::isValidDocumentId).distinct()
            val winners = if (entrantIds.isEmpty()) {
                emptyList()
            } else {
                // The campsite editor guards normal writes. Bound existing inconsistent records
                // by the live capacity, since each winner may choose any listed campsite.
                val smallestOccupancy = campsites.values.minOf {
                    checkNotNull(it.getLong("occupancy")) { "A participating campsite has no occupancy." }
                }
                check(smallestOccupancy > 0) { "A participating campsite has no usable occupancy." }
                selectWinners(entrantIds, lottery.numWinners, smallestOccupancy, drawRandom)
            }

            updateLotteryCopies(transaction, copies.campsiteIds, mapOf("winners" to winners, "drawn" to true))
            activeCampsites.forEach { transaction.update(it.reference, "inLottery", false) }
            for (entrantId in winners) {
                val invitationId = "${lottery.id}_$entrantId"
                transaction.set(
                    db.collection("reservations").document(invitationId),
                    Reservation(
                        id = invitationId,
                        lotteryId = lottery.id,
                        ownerId = ownerId,
                        entrantId = entrantId,
                        status = ReservationStatus.INVITED
                    )
                )
            }
            if (entrantIds.isNotEmpty()) {
                // A committed result can be delivered after a restart or an inbox write failure.
                transaction.set(db.collection("lotteryDrawResults").document(lottery.id), mapOf(
                    "campsiteName" to primaryCampsiteId,
                    "remainingEntrantIds" to entrantIds,
                    "winnerIds" to winners
                ))
            }
            LotteryDrawOutcome.Committed(lotteryId)
        }

    private fun isDrawDue(lottery: Lottery, now: LocalDateTime): Boolean =
        lottery.numWinners > 0 && !now.isBefore(lottery.period.registrationEnd)

    private fun drawCandidateDelayMillis(lottery: Lottery, hasStaleFlag: Boolean, now: LocalDateTime): Long? =
        when {
            lottery.drawn && hasStaleFlag -> 0L
            lottery.drawn || lottery.numWinners < 1 -> null
            isDrawDue(lottery, now) -> 0L
            else -> Duration.between(now, lottery.period.registrationEnd).toMillis().coerceAtLeast(1)
        }

    private fun selectWinners(
        entrantIds: List<String>,
        configuredWinnerCount: Int,
        smallestOccupancy: Long,
        random: Random
    ): List<String> =
        entrantIds.shuffled(random).take(minOf(configuredWinnerCount.toLong(), smallestOccupancy).toInt())

    /** Capacity changes must preserve outstanding places for undrawn and drawn lotteries. */
    internal fun checkCampsiteOccupancyReduction(transaction: Transaction, campsiteId: String, occupancy: Int) {
        val current = transaction.get(lotteryDocument(campsiteId))
        if (current.exists() && current.getBoolean("drawn") != true) {
            val copies = checkNotNull(readLotteryCopies(transaction, campsiteId)) {
                "The active lottery could not be read. Try again before reducing occupancy."
            }
            val configuredWinners = copies.primarySnapshot.getLong("numWinners") ?: 1L
            check(occupancy >= configuredWinners) {
                "Minimum occupancy is $configuredWinners while this campsite has an undrawn lottery."
            }
        }

        // Find future stay windows after reading the campsite parent. Creating a lottery or
        // changing its booking window also writes that parent, so concurrent changes retry.
        val schedules = db.collection("bookingSchedules").whereArrayContains("campsiteIds", campsiteId)
        val matchingSchedules = Tasks.await(schedules.get(Source.SERVER), 15, TimeUnit.SECONDS)
        val now = LocalDateTime.now(clock)
        for (record in matchingSchedules.documents) {
            val schedule = checkNotNull(BookingScheduleRepository.fromSnapshot(transaction.get(record.reference))) {
                "A booking schedule could not be read. Try again before reducing occupancy."
            }
            if (!schedule.periodFor(campsiteId).endDate.isAfter(now)) continue
            val lotteryId = schedule.registration.id
            check(isValidDocumentId(lotteryId)) { "The booking schedule has an invalid lottery ID." }
            val copies = checkNotNull(readLotteryCopies(transaction, campsiteId, lotteryId, includeHistory = true)) {
                "A lottery with a future stay could not be read. Try again before reducing occupancy."
            }
            val snapshot = copies.primarySnapshot
            if (snapshot.getBoolean("drawn") == true) {
                val winners = readStringList(snapshot, "winners").distinct().size
                check(occupancy >= winners) {
                    "Minimum occupancy is $winners until this lottery's stay window ends."
                }
            }
        }
    }

    // Joining and leaving the waitlist

    /**
     * Removes the user from every campsite's waitlist and their saved joined lotteries.
     * Updates the local user only after the transaction commits.
     * Calls [onResult] with false if the user cannot leave or the transaction fails.
     * [expectedLotteryId] rejects a replacement lottery when the screen is stale.
     */
    fun leaveLotteryWaitlist(
        campsiteName: String,
        user: User,
        expectedLotteryId: String? = null,
        onResult: (Boolean) -> Unit
    ) {
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

            // Check whether the user can leave.
            if ((expectedLotteryId != null && lottery.id != expectedLotteryId) || !canLeaveLotteryWaitlist(user, lottery)) {
                return@runTransaction null
            }

            val userDocument = db.collection("users").document(entrantId)
            val userSnapshot = transaction.get(userDocument)

            check(userSnapshot.exists()) {
                "The entrant account no longer exists."
            }

            val existingLotteries = readJoinedLotteries(userSnapshot)

            // Remove this lottery from the user's joined lotteries.
            val joinedLotteries = existingLotteries.filterNot { entry ->
                (entry as? Map<*, *>)?.get("lotteryId") == lottery.id
            }

            // Remove the entrant from every participating lottery copy.
            val updatedWaitlist = lottery.waitlistIds.filterNot {
                it == entrantId
            }

            updateLotteryCopies(
                transaction,
                lotteryCopies.campsiteIds,
                mapOf("waitlist" to updatedWaitlist)
            )

            // Update the user's joined lotteries in Firebase.
            transaction.update(
                userDocument,
                "joinedLotteries",
                joinedLotteries
            )

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
     * entrant or admin, they created/own the lottery or a participating campsite,
     * the lottery can't be joined, or the operation failed
     */
    fun joinLotteryWaitlist(
        campsiteName: String,
        user: User,
        expectedLotteryId: String,
        onResult: (JoinWaitlistResult) -> Unit
    ) {
        if (!canEnterLottery(user.userType)) {
            onResult(JoinWaitlistResult.INELIGIBLE)
            return
        }

        val entrantId = user.deviceID
        if (!isValidDocumentId(campsiteName) || !isValidDocumentId(entrantId) || !isValidDocumentId(expectedLotteryId)) {
            onResult(JoinWaitlistResult.INELIGIBLE)
            return
        }

        db.runTransaction<JoinWaitlistResult> { transaction ->
            val lotteryCopies = readLotteryCopies(transaction, campsiteName, expectedLotteryId)
                ?: return@runTransaction JoinWaitlistResult.LISTING_CHANGED

            val lottery = parseLottery(lotteryCopies.primarySnapshot)
                ?: return@runTransaction JoinWaitlistResult.LISTING_CHANGED

            // Read current ownership inside the transaction. A concurrent ownership change
            // forces a retry instead of letting an owner join through a stale screen.
            val campsiteSnapshots = lotteryCopies.campsiteIds.map { id ->
                transaction.get(campsitesCollection.document(id))
            }
            if (campsiteSnapshots.any { !it.exists() } ||
                !canJoinWaitlist(lottery, entrantId, campsiteSnapshots.mapNotNull { it.getString("ownerID") },
                    LocalDateTime.now(clock))) {
                return@runTransaction JoinWaitlistResult.INELIGIBLE
            }

            val userDocument = db.collection("users").document(entrantId)
            val userSnapshot = transaction.get(userDocument)

            if (!userSnapshot.exists() || !canEnterLottery(userSnapshot.getString("userType").orEmpty())) {
                return@runTransaction JoinWaitlistResult.INELIGIBLE
            }

            val existingLotteries = readJoinedLotteries(userSnapshot)

            // Check whether the user has already joined this lottery.
            val alreadyJoined = existingLotteries.any { entry ->
                (entry as? Map<*, *>)?.get("lotteryId") == lottery.id
            }

            if (alreadyJoined) {
                return@runTransaction JoinWaitlistResult.INELIGIBLE
            }

            // Add the new lottery to the user's joined lotteries.
            val joinedLotteries = existingLotteries + mapOf(
                "lotteryId" to lottery.id,
                "campsiteName" to campsiteName
            )

            // Update all participating lottery copies.
            updateLotteryCopies(
                transaction,
                lotteryCopies.campsiteIds,
                mapOf("waitlist" to lottery.waitlistIds + entrantId)
            )

            // Update the user's joined lotteries in Firebase.
            transaction.update(
                userDocument,
                "joinedLotteries",
                joinedLotteries
            )

            JoinWaitlistResult.JOINED
        }.addOnSuccessListener { result ->
            // The profile listener may already have delivered this committed membership.
            if (result == JoinWaitlistResult.JOINED && user.joinedLotteries.none { it.lotteryId == expectedLotteryId }) {
                user.joinedLotteries += JoinedLottery(expectedLotteryId, campsiteName)
            }

            onResult(result)

        }.addOnFailureListener {
            onResult(JoinWaitlistResult.FAILED)
        }
    }

    private fun canEnterLottery(userType: String): Boolean = userType.trim().lowercase() in setOf("entrant", "admin")

    /** Owners see their available campsites; admins can choose any available campsite. */
    fun availableCampsiteNames(user: User, campsites: Map<String, Campsite>): List<String> {
        if (!canCreateLotteries(user) || !isValidDocumentId(user.deviceID)) return emptyList()
        return campsites.filter { (campsiteId, campsite) ->
            isValidDocumentId(campsiteId) && !hasActiveLottery(campsite) && canManageCampsite(user, campsite)
        }.keys.sorted()
    }

    /** Returns a message for invalid options, or null when the booking step can be opened. */
    fun validateLotteryOptions(
        user: User,
        campsites: Map<String, Campsite>,
        campsiteName: String,
        additionalCampsiteIds: List<String>,
        numWinnersInput: String,
        waitlistCapacityInput: String,
        registrationStartInput: String,
        registrationEndInput: String,
        lotteryId: String,
        now: LocalDateTime = LocalDateTime.now(registrationZone)
    ): String? {
        if (!canCreateLotteries(user) || !isValidDocumentId(user.deviceID)) {
            return "Only a signed-in campsite owner or admin can create a lottery."
        }
        if (!isValidDocumentId(lotteryId)) return "Start a new lottery form before continuing."
        if (!isValidDocumentId(campsiteName)) return "Please select a valid campsite."

        val campsiteIds = listOf(campsiteName) + additionalCampsiteIds
        if (campsiteIds.any { !isValidDocumentId(it) }) return "Please select valid additional campsites."
        if (campsiteIds.distinct().size != campsiteIds.size) return "Select each campsite only once."

        for (campsiteId in campsiteIds) {
            val campsite = campsites[campsiteId] ?: return "Campsite $campsiteId is no longer available."
            if (!canManageCampsite(user, campsite)) return "You do not own campsite $campsiteId."
            if (hasActiveLottery(campsite)) return "Campsite $campsiteId is already in a lottery."
        }

        val numWinners = numWinnersInput.toIntOrNull()
        if (numWinners == null || numWinners < 1) return "Number of winners must be at least 1."
        for (campsiteId in campsiteIds) {
            if (numWinners > checkNotNull(campsites[campsiteId]).occupancy) {
                return "Number of winners exceeds the occupancy of $campsiteId."
            }
        }
        val waitlistCapacity = waitlistCapacityInput.toIntOrNull()
        if (waitlistCapacity == null || waitlistCapacity < 0) return "Waitlist capacity must be 0 or greater."

        val registrationStart = parseDateTime(registrationStartInput)
            ?: return "Please select a valid registration start."
        val registrationEnd = parseDateTime(registrationEndInput)
            ?: return "Please select a valid registration end."
        if (!registrationEnd.isAfter(registrationStart)) return "Registration must end after it starts."
        if (!registrationEnd.isAfter(now)) return "Registration must end in the future."
        return null
    }

    /** A shared period must follow registration and include a whole night the reservation UI can book. */
    fun validateBookingPeriod(
        registrationEndInput: String,
        bookingStartInput: String,
        bookingEndInput: String
    ): String? {
        val registrationEnd = parseDateTime(registrationEndInput)
            ?: return "Please select a valid registration end first."
        val bookingStart = parseDateTime(bookingStartInput)
            ?: return "Please select a valid booking start."
        val bookingEnd = parseDateTime(bookingEndInput)
            ?: return "Please select a valid booking end."
        return Booking.validationError(bookingStart, bookingEnd, registrationEnd)
    }

    /** Use loaded lottery state; fall back to the flag until its records are available. */
    private fun hasActiveLottery(campsite: Campsite): Boolean =
        campsite.lotteryCollection?.takeIf { it.isNotEmpty() }?.any { !it.drawn } ?: campsite.inLottery

    private fun canCreateLotteries(user: User): Boolean =
        user.userType.equals("admin", ignoreCase = true) ||
            user.userType.equals("campsite owner", ignoreCase = true)

    private fun canManageCampsite(user: User, campsite: Campsite): Boolean =
        user.userType.equals("admin", ignoreCase = true) || campsite.ownerID == user.deviceID


    private fun parseDateTime(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    fun validateCreationInputs(
        user: User, campsites: Map<String, Campsite>, campsiteName: String, additionalCampsiteIds: List<String>,
        numWinnersInput: String, waitlistCapacityInput: String, registrationStartInput: String, registrationEndInput: String,
        bookingStartInput: String, bookingEndInput: String, lotteryId: String,
        campsiteBookingStarts: Map<String, String>, campsiteBookingEnds: Map<String, String>,
        now: LocalDateTime = LocalDateTime.now(registrationZone)
    ): String? {
        val error = validateLotteryOptions(user, campsites, campsiteName, additionalCampsiteIds, numWinnersInput,
            waitlistCapacityInput, registrationStartInput, registrationEndInput, lotteryId, now)
            ?: validateBookingPeriod(registrationEndInput, bookingStartInput, bookingEndInput)
        if (error != null) return error
        val selectedIds = listOf(campsiteName) + additionalCampsiteIds
        for (id in campsiteBookingStarts.keys + campsiteBookingEnds.keys) {
            if (id !in selectedIds) return "Booking periods must belong to selected campsites."
            val start = parseDateTime(campsiteBookingStarts[id] ?: bookingStartInput)
            val end = parseDateTime(campsiteBookingEnds[id] ?: bookingEndInput)
            if (start == null || end == null) return "$id: Select valid booking dates."
            Booking.validationError(start, end, LocalDateTime.parse(registrationEndInput))?.let { return "$id: $it" }
        }
        return null
    }

    // Creating and saving

    /**
     * Creates all lottery copies and their shared booking schedule in one transaction.
     * A drawn current lottery is archived before replacement; its booking schedule is retained.
     * Rechecks ownership, occupancy, availability, and booking conflicts against Firestore.
     * No existing lottery or schedule is replaced when a check or write fails.
     *
     * [bookingStart] and [bookingEnd] define the default stay window for every campsite.
     * [campsitePeriods] contains optional overrides, not the complete list of periods.
     * A campsite without an override uses the default, as does one added later.
     */
    fun createLottery(
        user: User,
        lotteryId: String,
        campsiteIds: List<String>,
        period: Registration,
        numWinners: Int,
        waitlistCapacity: Int,
        bookingStart: LocalDateTime,
        bookingEnd: LocalDateTime,
        campsitePeriods: Map<String, Booking> = emptyMap()
    ): Task<Void> {
        val selectedCampsiteIds = campsiteIds.toList()
        val selectedPeriods = campsitePeriods.toMap()
        val lottery = Lottery(id = lotteryId, campsites = selectedCampsiteIds.associateWith(campsiteLookup),
            period = Registration(period.registrationStart, period.registrationEnd), numWinners = numWinners,
            waitlistCapacity = waitlistCapacity,
            createdById = user.deviceID,
            bookingSchedule = com.example.myapplication.features.lottery.BookingScheduleReference(lotteryId))
        val userId = user.deviceID
        val isAdmin = user.userType.equals("admin", ignoreCase = true)
        val isOwner = user.userType.equals("campsite owner", ignoreCase = true)
        val requestedStart = lottery.period.registrationStart
        val requestedEnd = lottery.period.registrationEnd
        val defaultBookingError = Booking.validationError(bookingStart, bookingEnd, lottery.period.registrationEnd)
        val validationError = when {
            !isValidDocumentId(userId) || (!isAdmin && !isOwner) ->
                "Only an administrator or campsite owner can create a lottery."
            !isValidDocumentId(lotteryId) -> "Invalid lottery ID."
            selectedCampsiteIds.isEmpty() || selectedCampsiteIds.any { !isValidDocumentId(it) } ||
                selectedCampsiteIds.distinct().size != selectedCampsiteIds.size ->
                "Select unique, valid campsites."
            defaultBookingError != null -> defaultBookingError
            selectedPeriods.keys.any { it !in selectedCampsiteIds } -> "Booking periods must belong to selected campsites."
            selectedPeriods.any { (id, booking) -> booking.entrantId != null || booking.campsiteId != id } ->
                "Campsite stay windows must be unassigned bookings for their campsite."
            else -> selectedPeriods.entries.firstNotNullOfOrNull { (id, period) ->
                period.validationError(lottery.period.registrationEnd)?.let { "$id: $it" }
            } ?: lotteryValidationError(lottery)
        }
        if (validationError != null) {
            return Tasks.forException(IllegalArgumentException(validationError))
        }

        val schedule = BookingSchedule(lottery, bookingStart, bookingEnd, selectedPeriods)
        // Capture wire values before the asynchronous transaction; callers may keep editing their objects.
        val creationFields = lotteryFields(lottery, selectedCampsiteIds)
        val scheduleFields = BookingScheduleRepository.toMap(schedule)
        val schedulesCollection = db.collection("bookingSchedules")
        val scheduleDocument = schedulesCollection.document(lotteryId)
        return db.runTransaction<Void> { transaction ->
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
                requestedStart,
                LocalDateTime.now(registrationZone)
            )
            check(requestedEnd.isAfter(registrationStart)) {
                "Registration must end in the future."
            }
            for ((campsiteId, campsiteSnapshot) in campsiteSnapshots) {
                check(campsiteSnapshot.exists()) { "Campsite $campsiteId no longer exists." }
                check(isAdmin || campsiteSnapshot.getString("ownerID") == userId) {
                    "You must own every selected campsite to create this lottery."
                }
                val occupancy = campsiteSnapshot.getLong("occupancy")
                check(occupancy != null && occupancy >= numWinners) {
                    "Campsite $campsiteId must have room for at least $numWinners winners."
                }
            }
            // The current document may belong to a completed lottery. Replace only drawn
            // lotteries; a missing drawn flag is treated as undrawn for legacy records.
            // Read this inside the transaction so concurrent creations cannot replace each other.
            check(existingLotterySnapshots.all { !it.exists() || it.getBoolean("drawn") == true }) {
                "A selected campsite has an undrawn lottery. Wait until it has been drawn."
            }

            checkBookingConflicts(transaction, selectedCampsiteIds, lotteryId, bookingStart, bookingEnd, selectedPeriods)

            val primaryCampsite = checkNotNull(campsiteSnapshots[selectedCampsiteIds.first()])
            val ownerId = primaryCampsite.getString("ownerID").orEmpty()
            check(isValidDocumentId(ownerId)) { "The primary campsite needs a valid owner." }
            val lotteryData = creationFields + mapOf(
                "ownerId" to ownerId, "registrationStart" to registrationStart.toString()
            )

            // Every read and validation has finished. These writes commit or fail together.
            existingLotterySnapshots.filter { it.exists() }.forEach { previous ->
                val previousId = checkNotNull(previous.getString("id")) { "The previous lottery needs an ID." }
                check(isValidDocumentId(previousId)) { "The previous lottery has an invalid ID." }
                // Same collection group as current, so live listeners include this completed lottery.
                transaction.set(
                    previous.reference.parent.document("history_$previousId"),
                    checkNotNull(previous.data) + ("isHistorical" to true)
                )
            }
            for (campsiteId in selectedCampsiteIds) {
                transaction.set(
                    lotteryDocument(campsiteId),
                    lotteryData + ("isPrimary" to (campsiteId == selectedCampsiteIds.first()))
                )
                transaction.update(campsitesCollection.document(campsiteId), mapOf("inLottery" to true))
            }
            transaction.set(scheduleDocument, scheduleFields + ("ownerId" to ownerId))
            null
        }
    }

    /**
     * Changes stay windows without replacing bookings or lottery results.
     * Supply [lotteryId] when editing a selected current or historical lottery.
     * Omitting it retains the current-lottery behavior for existing callers.
     */
    fun updateBookingPeriods(
        user: User,
        campsiteName: String,
        bookingStart: LocalDateTime,
        bookingEnd: LocalDateTime,
        campsitePeriods: Map<String, Booking> = emptyMap(),
        lotteryId: String? = null
    ): Task<BookingSchedule<Lottery>> {
        if (!isValidDocumentId(campsiteName)) return Tasks.forException(IllegalArgumentException("Invalid campsite ID."))
        if (lotteryId != null && !isValidDocumentId(lotteryId)) return Tasks.forException(IllegalArgumentException("Invalid lottery ID."))
        if (!isValidDocumentId(user.deviceID) ||
            (!user.userType.equals("admin", true) && !user.userType.equals("campsite owner", true))) {
            return Tasks.forException(IllegalArgumentException("Only a signed-in campsite owner or admin may change booking periods."))
        }
        val periods = campsitePeriods.toMap()
        return db.runTransaction<BookingSchedule<Lottery>> { transaction ->
            val copies = checkNotNull(readLotteryCopies(transaction, campsiteName, expectedLotteryId = lotteryId, includeHistory = true)) { "Lottery copies are missing or inconsistent." }
            val lottery = checkNotNull(parseLottery(copies.primarySnapshot))
            val campsites = copies.campsiteIds.map { transaction.get(campsitesCollection.document(it)) }
            check(campsites.all { it.exists() && canEditCampsite(user, it) }) { "Only an admin or owner of every campsite may change booking periods." }
            val document = db.collection("bookingSchedules").document(lottery.id)
            val saved = checkNotNull(BookingScheduleRepository.fromSnapshot(transaction.get(document))) { "The booking schedule is missing or invalid." }
            check(periods.keys.all { it in copies.campsiteIds }) { "Booking periods must belong to selected campsites." }
            check(periods.all { (id, booking) -> booking.entrantId == null && booking.campsiteId == id }) {
                "Campsite stay windows must be unassigned bookings for their campsite."
            }
            check(Booking.validationError(bookingStart, bookingEnd, lottery.period.registrationEnd) == null) { "Choose a valid default period after registration, including a full night." }
            periods.forEach { (id, period) ->
                check(period.validationError(lottery.period.registrationEnd) == null) { "$id: Choose a valid booking period after registration, including a full night." }
            }
            val updated = BookingSchedule(lottery, bookingStart, bookingEnd, periods)
            saved.getBookings().forEach { booking ->
                check(updated.periodFor(booking.campsiteId).contains(booking.startDate, booking.endDate)) { "The new period excludes an existing reservation at ${booking.campsiteId}." }
                updated.restoreBooking(booking)
            }
            checkBookingConflicts(transaction, copies.campsiteIds, lottery.id, bookingStart, bookingEnd, periods)
            transaction.set(document, BookingScheduleRepository.toMap(updated))
            // Creation reads these parents too, so concurrent period edits and creations retry safely.
            copies.campsiteIds.forEach { id ->
                transaction.update(campsitesCollection.document(id), "bookingScheduleUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
            }
            updated
        }
    }

    /** Discover schedules, then transactionally re-read them before any writes. */
    private fun checkBookingConflicts(
        transaction: Transaction,
        campsiteIds: List<String>,
        lotteryId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        periods: Map<String, Booking>
    ) {
        val queries = campsiteIds.chunked(10).map {
            db.collection("bookingSchedules").whereArrayContainsAny("campsiteIds", it).get(Source.SERVER)
        }
        val results = Tasks.await(Tasks.whenAllSuccess<QuerySnapshot>(queries), 15, TimeUnit.SECONDS)
        results.flatMap { it.documents }.distinctBy { it.id }.filterNot { it.id == lotteryId }.forEach { snapshot ->
            val saved = checkNotNull(BookingScheduleRepository.fromSnapshot(transaction.get(snapshot.reference))) {
                "An existing booking schedule could not be read."
            }
            campsiteIds.filter { it in saved.registration.campsiteIds }.forEach { id ->
                val existing = saved.periodFor(id)
                check(existing.endDate.isAfter(existing.startDate)) { "An existing booking schedule has invalid dates." }
                check(!(periods[id] ?: Booking(campsiteId = id, startDate = start, endDate = end)).overlaps(existing)) {
                    "The booking period overlaps an existing schedule for $id."
                }
            }
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
            "createdById" to lottery.createdById,
            "bookingScheduleId" to lottery.bookingSchedule.id,
            "campsiteIds" to campsiteIds,
            "numWinners" to lottery.numWinners,
            "waitlistCapacity" to lottery.waitlistCapacity,
            "waitlist" to lottery.waitlistIds,
            "winners" to lottery.winners.toList(),
            "drawn" to lottery.drawn,
            "registrationStart" to lottery.period.registrationStart.toString(),
            "registrationEnd" to lottery.period.registrationEnd.toString(),
            "registrationEndAt" to Timestamp(closingTime.epochSecond, closingTime.nano)
        )
    }

    /**
     * Checks that occupancy supports the draw, before any transaction writes.
     */
    private fun checkCampsiteOccupancy(
        transaction: Transaction,
        campsiteId: String,
        numWinners: Int
    ) {
        val occupancy = transaction.get(campsitesCollection.document(campsiteId)).getLong("occupancy")
        check(occupancy != null) {
            "Set the campsite occupancy before changing the lottery."
        }
        check(numWinners <= occupancy) {
            "Number of winners exceeds the campsite occupancy."
        }
    }

    // Listing updates

    /**
     * Updates the existing listing and its secondary copies in one transaction.
     * Checks current ownership, membership count, occupancy, and booking periods before writing.
     * @param expectedLotteryId If supplied, rejects a different lottery that replaced the
     * selected listing while its form was open. Historical/drawn records are not editable.
     * @return Task that commits all listing copies together, or fails without saving changes.
     */
    fun updateLottery(user: User, campsiteName: String, period: Registration,
        numWinners: Int, waitlistCapacity: Int, expectedLotteryId: String? = null): Task<Void> {
        val start = period.registrationStart
        val end = period.registrationEnd
        if (!end.isAfter(start) || numWinners < 1 || waitlistCapacity < 0) {
            return Tasks.forException(IllegalArgumentException("Invalid registration dates, winner count, or capacity."))
        }
        return editLottery(user, campsiteName, expectedLotteryId) { transaction, copies, saved ->
            val schedule = checkNotNull(BookingScheduleRepository.fromSnapshot(
                transaction.get(db.collection("bookingSchedules").document(saved.bookingSchedule.id)))) {
                "The booking schedule is missing or invalid."
            }
            val occupancies = copies.campsiteIds.associateWith { id ->
                transaction.get(campsitesCollection.document(id)).getLong("occupancy")?.toInt() ?: 0
            }
            validateLotteryListingDetails(saved, occupancies, schedule, start, end, numWinners, waitlistCapacity)?.let { error ->
                throw IllegalStateException(error)
            }
            val closing = end.atZone(registrationZone).toInstant()
            updateLotteryCopies(transaction, copies.campsiteIds, mapOf("registrationStart" to start.toString(),
                "registrationEnd" to end.toString(), "registrationEndAt" to Timestamp(closing.epochSecond, closing.nano),
                "numWinners" to numWinners, "waitlistCapacity" to waitlistCapacity))
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
            if (updatedWaitlist != savedLottery.waitlistIds) {
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
        val removedEntrantIds = savedLottery.waitlistIds.filterNot { it in updatedWaitlist }
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

            val scheduleDocument = db.collection("bookingSchedules").document(savedLottery.id)
            val schedule = checkNotNull(BookingScheduleRepository.fromSnapshot(transaction.get(scheduleDocument))) {
                "The lottery's booking schedule is missing or invalid."
            }
            checkBookingConflicts(transaction, listOf(newCampsiteName), savedLottery.id,
                schedule.bookingStart, schedule.bookingEnd, emptyMap())
            val updatedCampsiteIds = lotteryCopies.campsiteIds + newCampsiteName
            transaction.update(scheduleDocument, "campsiteIds", updatedCampsiteIds)
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
                checkCampsiteOccupancy(transaction, newPrimaryCampsiteId, savedLottery.numWinners)
            }

            // Entries made through the removed campsite should point to one that remains.
            // Read every affected user before any writes to keep join references consistent.
            val userChanges = mutableMapOf<String, List<Any>>()
            for (entrantId in savedLottery.waitlistIds) {
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

            val scheduleDocument = db.collection("bookingSchedules").document(savedLottery.id)
            val schedule = checkNotNull(BookingScheduleRepository.fromSnapshot(transaction.get(scheduleDocument))) {
                "The lottery's booking schedule is missing or invalid."
            }
            check(schedule.getBookings().none { it.campsiteId == removeCampsiteName }) {
                "Cannot remove a campsite with existing bookings."
            }
            // All Firestore reads are finished. Keep every remaining copy and schedule in sync.
            transaction.update(scheduleDocument, mapOf(
                "campsiteIds" to remainingCampsiteIds,
                "campsitePeriods" to schedule.campsitePeriods.filterKeys { it in remainingCampsiteIds }.mapValues { (_, p) ->
                    mapOf("start" to p.startDate.toString(), "end" to p.endDate.toString())
                }
            ))
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
        expectedLotteryId: String? = null,
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
            check(expectedLotteryId == null || savedLottery.id == expectedLotteryId) {
                "This listing has been replaced. Reopen lottery information before editing."
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
        user.deviceID.isNotBlank() && (user.userType.trim().equals("admin", ignoreCase = true) ||
            (user.userType.trim().equals("campsite owner", ignoreCase = true) && campsite.getString("ownerID") == user.deviceID))

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
     * Returns null for missing or inconsistent copies. Schedule edits may include archived copies.
     */
    private fun readLotteryCopies(
        transaction: Transaction,
        campsiteName: String,
        expectedLotteryId: String? = null,
        includeHistory: Boolean = false
    ): LotteryCopies? {
        fun readCopy(id: String): DocumentSnapshot {
            val current = transaction.get(lotteryDocument(id))
            return if (includeHistory && expectedLotteryId != null && current.getString("id") != expectedLotteryId) {
                transaction.get(historicalLotteryDocument(id, expectedLotteryId))
            } else current
        }
        val selectedSnapshot = readCopy(campsiteName)
        val lotteryId = selectedSnapshot.getString("id")
        if (lotteryId.isNullOrBlank()) return null
        if (expectedLotteryId != null && lotteryId != expectedLotteryId) return null

        // The first nonblank campsite ID identifies the primary copy.
        val storedCampsiteIds = readStringList(selectedSnapshot, "campsiteIds")
        val primaryCampsiteId = storedCampsiteIds.firstOrNull { it.isNotBlank() } ?: campsiteName
        if (!isValidDocumentId(primaryCampsiteId)) return null

        val primarySnapshot = if (primaryCampsiteId == campsiteName) {
            selectedSnapshot
        } else {
            readCopy(primaryCampsiteId)
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
                else -> readCopy(campsiteId)
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
    internal fun parseLottery(snapshot: DocumentSnapshot): Lottery? {
        if (!snapshot.exists()) return null

        val period = Registration(
            registrationStart = readRegistrationDate(snapshot, "registrationStart"),
            registrationEnd = readRegistrationDate(snapshot, "registrationEnd")
        )
        return Lottery(
            id = snapshot.getString("id").orEmpty(),
            ownerId = snapshot.getString("ownerId").orEmpty(),
            createdById = snapshot.getString("createdById").orEmpty(),
            campsites = readStringList(snapshot, "campsiteIds").associateWith(campsiteLookup),
            period = period,
            numWinners = snapshot.getLong("numWinners")?.toInt() ?: 1,
            waitlistCapacity = snapshot.getLong("waitlistCapacity")?.toInt() ?: 0,
            waitlist = readStringList(snapshot, "waitlist").associateWith(userLookup),
            winners = readStringList(snapshot, "winners"),
            drawn = snapshot.getBoolean("drawn") ?: false,
            bookingSchedule = com.example.myapplication.features.lottery.BookingScheduleReference(
                snapshot.getString("bookingScheduleId") ?: snapshot.getString("id").orEmpty())
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

    /**
     * Accepts raw edit-form values, validates them in the repository, and updates the
     * existing listing. The transaction rechecks current records before writing.
     * Screens do not parse inputs or construct replacement lottery models.
     * @return A failed task for invalid details, unavailable data, or a rejected save.
     */
    fun updateLotteryFromInput(
        user: User?, lottery: Lottery?, campsites: Map<String, Campsite>,
        registrationStartInput: String, registrationEndInput: String,
        numWinnersInput: String, waitlistCapacityInput: String,
        bookingSchedule: BookingSchedule<Lottery>?
    ): Task<Void> {
        val details = validatedListingInputs(user, lottery, campsites, registrationStartInput,
            registrationEndInput, numWinnersInput, waitlistCapacityInput, bookingSchedule)
            .getOrElse { return Tasks.forException(IllegalArgumentException(it.message, it)) }
        val saved = checkNotNull(lottery)
        return updateLottery(
            checkNotNull(user), saved.campsiteIds.first(),
            Registration(details.registrationStart, details.registrationEnd),
            details.numWinners, details.waitlistCapacity, expectedLotteryId = saved.id
        )
    }

    companion object {
        /**
         * Registration must be open and have space. A creator, recorded owner, or owner
         * of a participating campsite cannot join, even with an admin account.
         * @param campsiteOwnerIds Current parent-document owner IDs. Persistence supplies
         * transaction reads; the UI supplies the controller's loaded campsite records.
         * @param now Current Alberta time used for registration boundaries.
         */
        fun canJoinWaitlist(
            lottery: Lottery,
            entrantId: String,
            campsiteOwnerIds: Collection<String> = lottery.campsites.values.mapNotNull { it?.ownerID },
            now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
        ): Boolean = entrantId.isNotBlank() && '/' !in entrantId &&
            entrantId != lottery.createdById && entrantId != lottery.ownerId &&
            entrantId !in campsiteOwnerIds && !lottery.drawn &&
            lottery.period.registrationIsOpen(now) &&
            entrantId !in lottery.waitlist && entrantId !in lottery.winners &&
            (lottery.waitlistCapacity <= 0 || lottery.waitlist.size < lottery.waitlistCapacity)
        /** Owners must own every participating campsite; admins may edit undrawn listings. */
        fun canEditLotteryListing(user: User?, lottery: Lottery, campsites: Map<String, Campsite>): Boolean {
            if (user == null || user.deviceID.isBlank() || lottery.drawn || lottery.campsiteIds.isEmpty()) return false
            return when (user.userType.trim().lowercase()) {
                "admin" -> true
                "campsite owner" -> lottery.campsiteIds.all { campsites[it]?.ownerID == user.deviceID }
                else -> false
            }
        }

        /** Registration closing does not prevent leaving; membership and draw state do. */
        fun canLeaveLotteryWaitlist(user: User?, lottery: Lottery): Boolean =
            user != null && user.deviceID.isNotBlank() &&
                user.userType.trim().lowercase() in setOf("entrant", "admin") &&
                !lottery.drawn && user.deviceID in lottery.waitlist

        /** Whether a profile may view entrant details for this lottery. */
        fun canViewLotteryWaitlist(user: User?, lottery: Lottery, campsites: Map<String, Campsite>): Boolean {
            if (user == null) return false
            return when (user.userType.trim().lowercase()) {
                "admin" -> true
                "campsite owner" -> lottery.ownerId == user.deviceID ||
                    lottery.campsiteIds.any { campsites[it]?.ownerID == user.deviceID }
                else -> false
            }
        }

        /** Selects the latest undrawn lottery involving the given campsite. */
        fun currentCampsiteLottery(campsite: Campsite): Lottery? = campsite.lotteryCollection.orEmpty()
            .filter { campsite.name in it.campsiteIds && !it.drawn }
            .maxByOrNull { it.period.registrationStart }

        /**
         * Validates raw listing inputs without writes. Returns the first error, or null.
         * Used by the edit action and tests; all parsing and business checks live here.
         * @param bookingSchedule Required default and per-campsite stay windows.
         * @param now Alberta time used to reject registration ends in the past.
         */
        fun validateLotteryListingInputs(
            user: User?, lottery: Lottery?, campsites: Map<String, Campsite>,
            registrationStartInput: String, registrationEndInput: String,
            numWinnersInput: String, waitlistCapacityInput: String,
            bookingSchedule: BookingSchedule<Lottery>?,
            now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
        ): String? = validatedListingInputs(
            user, lottery, campsites, registrationStartInput, registrationEndInput,
            numWinnersInput, waitlistCapacityInput, bookingSchedule, now
        ).exceptionOrNull()?.message

        private fun validatedListingInputs(
            user: User?, lottery: Lottery?, campsites: Map<String, Campsite>,
            registrationStartInput: String, registrationEndInput: String,
            numWinnersInput: String, waitlistCapacityInput: String,
            bookingSchedule: BookingSchedule<Lottery>?,
            now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
        ): Result<ListingDetails> = runCatching {
            require(lottery != null && canEditLotteryListing(user, lottery, campsites)) {
                "This lottery is unavailable, already drawn, or you do not have permission to edit it."
            }
            val start = parseListingDate(registrationStartInput, "Please select a valid registration start.")
            val end = parseListingDate(registrationEndInput, "Please select a valid registration end.")
            val winners = requireNotNull(numWinnersInput.trim().toIntOrNull()) { "Enter a whole number of winners." }
            val capacity = requireNotNull(waitlistCapacityInput.trim().toIntOrNull()) { "Enter a whole number for waitlist capacity." }
            requireNotNull(bookingSchedule) { "The booking schedule is missing or invalid." }
            val error = validateLotteryListingDetails(
                lottery, campsites.mapValues { it.value.occupancy }, bookingSchedule, start, end, winners, capacity, now
            )
            if (error != null) throw IllegalArgumentException(error)
            ListingDetails(start, end, winners, capacity)
        }

        private fun parseListingDate(input: String, message: String): LocalDateTime =
            try {
                LocalDateTime.parse(input.trim())
            } catch (error: DateTimeParseException) {
                throw IllegalArgumentException(message, error)
            }

        /** Validates the transaction's authoritative values before any listing write. */
        private fun validateLotteryListingDetails(
            lottery: Lottery, occupancyByCampsite: Map<String, Int>, bookingSchedule: BookingSchedule<Lottery>,
            registrationStart: LocalDateTime, registrationEnd: LocalDateTime,
            numWinners: Int, waitlistCapacity: Int,
            now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
        ): String? = when {
            lottery.drawn -> "A drawn lottery cannot be edited."
            lottery.campsiteIds.isEmpty() -> "The lottery has no available campsites."
            numWinners < 1 -> "Number of winners must be at least 1."
            waitlistCapacity < 0 -> "Waitlist capacity must be 0 or greater."
            !registrationEnd.isAfter(registrationStart) -> "Registration must end after it starts."
            !registrationEnd.isAfter(now) -> "Registration must end in the future."
            waitlistCapacity > 0 && waitlistCapacity < lottery.waitlist.size -> "Capacity cannot be less than the current waitlist."
            lottery.campsiteIds.any { it !in occupancyByCampsite } -> "A participating campsite is no longer available."
            lottery.campsiteIds.any { numWinners > occupancyByCampsite.getValue(it) } -> "Number of winners exceeds a participating campsite's occupancy."
            else -> bookingSchedule.registrationEndError(registrationEnd, lottery.campsiteIds)
        }
    }
}
