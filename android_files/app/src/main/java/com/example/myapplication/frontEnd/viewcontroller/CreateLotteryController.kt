package com.example.myapplication.frontEnd.viewcontroller

import com.example.myapplication.backEnd.time.AlbertaTime

import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.featureModels.BookingSchedule.Booking
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.Registration
import com.example.myapplication.backEnd.userModels.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDateTime

/** Delegates validation and persistence to the repository and tracks UI completion without navigation or view code. */
class CreateLotteryController(
    private val lotteryRepository: LotteryRepository,
    private val clock: Clock = Clock.system(AlbertaTime.zone)
) {
    data class CreationState(
        val isCreating: Boolean = false,
        val result: Result<Unit>? = null,
        val lotteryId: String? = null
    )


    /*The following models getters and setters in classes though in regards in composble functions */
    private val mutableCreationState = MutableStateFlow(CreationState())
    val creationState: StateFlow<CreationState> = mutableCreationState.asStateFlow()

    fun availableCampsiteNames(user: User, campsites: Map<String, Campsite>): List<String> =
        lotteryRepository.availableCampsiteNames(user, campsites)

    fun validateLotteryOptions(
        user: User, campsites: Map<String, Campsite>, campsiteName: String, additionalCampsiteIds: List<String>,
        numWinnersInput: String, waitlistCapacityInput: String, registrationStartInput: String,
        registrationEndInput: String, lotteryId: String
    ): String? = lotteryRepository.validateLotteryOptions(user, campsites, campsiteName, additionalCampsiteIds,
        numWinnersInput, waitlistCapacityInput, registrationStartInput, registrationEndInput, lotteryId, LocalDateTime.now(clock))

    fun validateBookingPeriod(registrationEndInput: String, bookingStartInput: String, bookingEndInput: String): String? =
        lotteryRepository.validateBookingPeriod(registrationEndInput, bookingStartInput, bookingEndInput)

    /**
     * Validates both steps through the repository, converts the valid form values,
     * then calls its regular createLottery operation with all creation parameters.
     * Firestore listeners synchronize campsite collections after persistence.
     */
    fun controllercreationLottery(
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
        campsiteBookingStarts: Map<String, String> = emptyMap(),
        campsiteBookingEnds: Map<String, String> = emptyMap(),
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        if (mutableCreationState.value.isCreating) {
            onResult(Result.failure(IllegalStateException("A lottery is already being created.")))
            return
        }
        val validationError = lotteryRepository.validateCreationInputs(user, campsites, campsiteName, additionalCampsiteIds, numWinnersInput,
            waitlistCapacityInput, registrationStartInput, registrationEndInput, bookingStartInput, bookingEndInput,
            lotteryId, campsiteBookingStarts, campsiteBookingEnds, LocalDateTime.now(clock))
        if (validationError != null) {
            finishCreation(lotteryId, Result.failure(IllegalArgumentException(validationError)), onResult)
            return
        }
        mutableCreationState.value = CreationState(isCreating = true, lotteryId = lotteryId)
        val campsitePeriods = (campsiteBookingStarts.keys + campsiteBookingEnds.keys).associateWith { id ->
            Booking(campsiteId = id,
                startDate = LocalDateTime.parse(campsiteBookingStarts[id] ?: bookingStartInput),
                endDate = LocalDateTime.parse(campsiteBookingEnds[id] ?: bookingEndInput))
        }
        lotteryRepository.createLottery(
            user = user,
            lotteryId = lotteryId,
            campsiteIds = listOf(campsiteName) + additionalCampsiteIds,
            period = Registration(LocalDateTime.parse(registrationStartInput), LocalDateTime.parse(registrationEndInput)),
            numWinners = numWinnersInput.toInt(),
            waitlistCapacity = waitlistCapacityInput.toInt(),
            bookingStart = LocalDateTime.parse(bookingStartInput),
            bookingEnd = LocalDateTime.parse(bookingEndInput),
            campsitePeriods = campsitePeriods
        ).addOnSuccessListener {
            finishCreation(lotteryId, Result.success(Unit), onResult)
        }.addOnFailureListener { exception ->
            finishCreation(lotteryId, Result.failure(exception), onResult)
        }
    }

    /** Delivers completion once and only to its requested lottery. */
    fun consumeCreationResult(
        lotteryId: String? = null
    ): Result<Unit>? {
        val state = mutableCreationState.value
        val result = state.result ?: return null
        mutableCreationState.value = state.copy(result = null)
        return if (lotteryId == null || lotteryId == state.lotteryId) result else null
    }

    private fun finishCreation(
        lotteryId: String,
        result: Result<Unit>,
        onResult: (Result<Unit>) -> Unit
    ) {
        mutableCreationState.value = CreationState(result = result, lotteryId = lotteryId)
        onResult(result)
    }

}
