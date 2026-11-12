package com.example.myapplication.frontEnd.viewcontroller

import com.example.myapplication.backEnd.time.AlbertaTime

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.example.myapplication.backEnd.`firebase-Repositories`.LotteryRepository
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.userModels.User
import com.example.myapplication.frontEnd.screens.LotteryViews.CreateLotteryScreen
import com.example.myapplication.frontEnd.screens.LotteryViews.PickBookingPeriodsScreen
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun CreateLotteryFlow(
    navController: NavController,
    lotteryRepository: LotteryRepository,
    user: User,
    campsites: Map<String, Campsite>
) {
    val controller = remember(lotteryRepository, user.deviceID) {
        CreateLotteryController(lotteryRepository)
    }
    CreateLotteryFlow(navController, controller, user, campsites)
}

/** Keeps the ordinary form values while moving between the two creation screens. */
@Composable
fun CreateLotteryFlow(
    navController: NavController,
    controller: CreateLotteryController,
    user: User,
    campsites: Map<String, Campsite>,
    initialCampsiteName: String = "",
    initialAdditionalCampsiteIds: List<String> = emptyList(),
    initialNumWinners: String = "1",
    initialWaitlistCapacity: String = "5",
    initialRegistrationStart: String? = null,
    initialRegistrationEnd: String? = null,
    initialBookingStart: String? = null,
    initialBookingEnd: String? = null,
    initialLotteryId: String? = null
) {
    val defaultStart = remember {
        LocalDateTime.now(AlbertaTime.zone)
            .withSecond(0).withNano(0)
    }
    val defaultEnd = defaultStart.plusDays(1)
    val defaultBookingStart = defaultEnd.toLocalDate().plusDays(1).atStartOfDay()

    val lotteryId = rememberSaveable(user.deviceID) { initialLotteryId ?: UUID.randomUUID().toString() }
    var campsiteName by rememberSaveable(user.deviceID) { mutableStateOf(initialCampsiteName) }
    var additionalCampsiteIds by rememberSaveable(user.deviceID) {
        mutableStateOf(initialAdditionalCampsiteIds.toList())
    }
    var numWinners by rememberSaveable(user.deviceID) { mutableStateOf(initialNumWinners) }
    var waitlistCapacity by rememberSaveable(user.deviceID) { mutableStateOf(initialWaitlistCapacity) }
    var registrationStart by rememberSaveable(user.deviceID) {
        mutableStateOf(initialRegistrationStart ?: defaultStart.toString())
    }
    var registrationEnd by rememberSaveable(user.deviceID) {
        mutableStateOf(initialRegistrationEnd ?: defaultEnd.toString())
    }
    var bookingStart by rememberSaveable(user.deviceID) {
        mutableStateOf(initialBookingStart ?: defaultBookingStart.toString())
    }
    var bookingEnd by rememberSaveable(user.deviceID) {
        mutableStateOf(initialBookingEnd ?: defaultBookingStart.plusDays(7).toString())
    }
    var pickingBookingPeriod by rememberSaveable(user.deviceID) { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val creationState by controller.creationState.collectAsState()
    val isCreating = creationState.isCreating

    LaunchedEffect(controller, creationState.result) {
        val result = controller.consumeCreationResult(campsites, lotteryId) ?: return@LaunchedEffect
        result.onSuccess {
            navController.popBackStack("HomeScreen", inclusive = false)
        }.onFailure { error ->
            errorMessage = error.message ?: "Unable to create the lottery. Please try again."
        }
    }

    if (pickingBookingPeriod) {
        PickBookingPeriodsScreen(
            campsiteName = campsiteName,
            additionalCampsiteIds = additionalCampsiteIds,
            numWinners = numWinners,
            registrationEnd = registrationEnd,
            bookingStart = bookingStart,
            bookingEnd = bookingEnd,
            errorMessage = errorMessage,
            isCreating = isCreating,
            onBookingStartChange = {
                bookingStart = it
                errorMessage = null
            },
            onBookingEndChange = {
                bookingEnd = it
                errorMessage = null
            },
            onBack = {
                if (!isCreating) {
                    pickingBookingPeriod = false
                    errorMessage = null
                }
            },
            onCreate = {
                // Guard the callback as well as disabling the button during submission.
                if (!isCreating) {
                    errorMessage = null
                    controller.createLottery(
                        user = user,
                        campsites = campsites,
                        campsiteName = campsiteName,
                        additionalCampsiteIds = additionalCampsiteIds,
                        numWinnersInput = numWinners,
                        waitlistCapacityInput = waitlistCapacity,
                        registrationStartInput = registrationStart,
                        registrationEndInput = registrationEnd,
                        bookingStartInput = bookingStart,
                        bookingEndInput = bookingEnd,
                        lotteryId = lotteryId
                    )
                }
            }
        )
    } else {
        BackHandler {
            navController.popBackStack("HomeScreen", inclusive = false)
        }
        CreateLotteryScreen(
            campsiteName = campsiteName,
            additionalCampsiteIds = additionalCampsiteIds,
            numWinners = numWinners,
            waitlistCapacity = waitlistCapacity,
            registrationStart = registrationStart,
            registrationEnd = registrationEnd,
            campsiteNames = controller.availableCampsiteNames(user, campsites),
            errorMessage = errorMessage,
            onCampsiteChange = { selectedName ->
                campsiteName = selectedName
                additionalCampsiteIds = additionalCampsiteIds.filterNot { it == selectedName }
                errorMessage = null
            },
            onAdditionalCampsitesChange = {
                additionalCampsiteIds = it.toList()
                errorMessage = null
            },
            onNumWinnersChange = {
                numWinners = it
                errorMessage = null
            },
            onWaitlistCapacityChange = {
                waitlistCapacity = it
                errorMessage = null
            },
            onRegistrationStartChange = {
                registrationStart = it
                errorMessage = null
            },
            onRegistrationEndChange = {
                registrationEnd = it
                errorMessage = null
            },
            onNext = {
                errorMessage = controller.validateLotteryOptions(
                    user = user,
                    campsites = campsites,
                    campsiteName = campsiteName,
                    additionalCampsiteIds = additionalCampsiteIds,
                    numWinnersInput = numWinners,
                    waitlistCapacityInput = waitlistCapacity,
                    registrationStartInput = registrationStart,
                    registrationEndInput = registrationEnd,
                    lotteryId = lotteryId
                )
                if (errorMessage == null) pickingBookingPeriod = true
            },
            onBack = { navController.popBackStack("HomeScreen", inclusive = false) }
        )
    }
}
