package com.example.myapplication.frontEnd.viewcontroller

import com.example.myapplication.backEnd.time.AlbertaTime

import com.example.myapplication.backEnd.`firebase-Repositories`.LotteryRepository
import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Registration
import com.example.myapplication.backEnd.userModels.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Validates the two creation steps and coordinates persistence without navigation or view code. */
class CreateLotteryController(
    private val lotteryRepository: LotteryRepository,
    private val clock: Clock = Clock.system(AlbertaTime.zone)
) {
    data class CreationState(
        val isCreating: Boolean = false,
        val result: Result<BookingSchedule<Lottery>>? = null,
        val lotteryId: String? = null
    )

    private val mutableCreationState = MutableStateFlow(CreationState())
    val creationState: StateFlow<CreationState> = mutableCreationState.asStateFlow()

    /** Owners see their available campsites; admins can choose any available campsite. */
    fun availableCampsiteNames(user: User, campsites: Map<String, Campsite>): List<String> {
        if (!canCreateLotteries(user) || !isValidDocumentId(user.deviceID)) return emptyList()
        return campsites.filter { (campsiteId, campsite) ->
            isValidDocumentId(campsiteId) && !campsite.inLottery && canManageCampsite(user, campsite)
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
        lotteryId: String
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
            if (campsite.inLottery) return "Campsite $campsiteId is already in a lottery."
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
        if (!registrationEnd.isAfter(LocalDateTime.now(clock))) return "Registration must end in the future."
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
        if (!bookingEnd.isAfter(bookingStart)) return "Booking must end after it starts."
        if (bookingStart.isBefore(registrationEnd)) return "Booking cannot start before registration closes."
        val midnight = bookingStart.toLocalDate().atStartOfDay()
        val firstNightStart = if (midnight.isBefore(bookingStart)) midnight.plusDays(1) else midnight
        if (firstNightStart.plusDays(1).isAfter(bookingEnd)) {
            return "Choose a booking period that includes at least one full night, from midnight to midnight."
        }
        return null
    }

    /**
     * Rechecks both steps, then asks the repository to save the lottery and schedule atomically.
     * Local campsite collections change only after that save succeeds.
     */
    fun createLottery(
        user: User,
        campsites: Map<String, Campsite>,
        campsiteName: String,
        additionalCampsiteIds: List<String>,
        numWinnersInput: String,
        waitlistCapacityInput: String,
        registrationStartInput: String,
        registrationEndInput: String,
        bookingStartInput: String,
        bookingEndInput: String,
        lotteryId: String,
        onResult: (Result<BookingSchedule<Lottery>>) -> Unit = {}
    ) {
        if (mutableCreationState.value.isCreating) {
            onResult(Result.failure(IllegalStateException("A lottery is already being created.")))
            return
        }
        val validationError = validateLotteryOptions(
            user, campsites, campsiteName, additionalCampsiteIds, numWinnersInput,
            waitlistCapacityInput, registrationStartInput, registrationEndInput, lotteryId
        ) ?: validateBookingPeriod(registrationEndInput, bookingStartInput, bookingEndInput)
        if (validationError != null) {
            finishCreation(lotteryId, Result.failure(IllegalArgumentException(validationError)), onResult)
            return
        }

        mutableCreationState.value = CreationState(isCreating = true, lotteryId = lotteryId)
        lotteryRepository.createLotteryWithBookingSchedule(
            user = user,
            lotteryId = lotteryId,
            campsiteIds = listOf(campsiteName) + additionalCampsiteIds,
            period = Registration(
                registrationStart = LocalDateTime.parse(registrationStartInput),
                registrationEnd = LocalDateTime.parse(registrationEndInput)
            ),
            numWinners = numWinnersInput.toInt(),
            waitlistCapacity = waitlistCapacityInput.toInt(),
            bookingStart = LocalDateTime.parse(bookingStartInput),
            bookingEnd = LocalDateTime.parse(bookingEndInput)
        ).addOnSuccessListener { schedule ->
            updateCampsiteCollections(campsites, schedule)
            finishCreation(lotteryId, Result.success(schedule), onResult)
        }.addOnFailureListener { exception ->
            finishCreation(lotteryId, Result.failure(exception), onResult)
        }
    }

    /** Syncs saved campsite data, delivering a result once and only to its requested lottery. */
    fun consumeCreationResult(
        campsites: Map<String, Campsite>,
        lotteryId: String? = null
    ): Result<BookingSchedule<Lottery>>? {
        val state = mutableCreationState.value
        val result = state.result ?: return null
        mutableCreationState.value = state.copy(result = null)
        result.onSuccess { schedule -> updateCampsiteCollections(campsites, schedule) }
        return if (lotteryId == null || lotteryId == state.lotteryId) result else null
    }

    private fun finishCreation(
        lotteryId: String,
        result: Result<BookingSchedule<Lottery>>,
        onResult: (Result<BookingSchedule<Lottery>>) -> Unit
    ) {
        mutableCreationState.value = CreationState(result = result, lotteryId = lotteryId)
        onResult(result)
    }

    private fun updateCampsiteCollections(
        campsites: Map<String, Campsite>,
        schedule: BookingSchedule<Lottery>
    ) {
        val lottery = schedule.registration
        for (campsiteId in lottery.campsiteIds) {
            // Resolve the latest object: Firestore listeners may have refreshed the map during saving.
            val campsite = campsites[campsiteId] ?: continue
            campsite.lotteryCollection = campsite.lotteryCollection.orEmpty().filterNot { it.id == lottery.id } + lottery
            campsite.bookingSchedules = (
                campsite.bookingSchedules.orEmpty().filterNot { (it.registration as? Lottery)?.id == lottery.id } + schedule
            ).toMutableList()
            campsite.inLottery = true
        }
    }

    private fun canCreateLotteries(user: User): Boolean =
        user.userType.equals("admin", ignoreCase = true) ||
            user.userType.equals("campsite owner", ignoreCase = true)

    private fun canManageCampsite(user: User, campsite: Campsite): Boolean =
        user.userType.equals("admin", ignoreCase = true) || campsite.ownerID == user.deviceID

    private fun isValidDocumentId(value: String): Boolean = value.isNotBlank() && '/' !in value

    private fun parseDateTime(value: String): LocalDateTime? = try {
        LocalDateTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
