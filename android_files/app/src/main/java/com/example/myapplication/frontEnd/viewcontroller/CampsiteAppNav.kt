package com.example.myapplication.frontEnd.viewcontroller

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.example.myapplication.frontEnd.screens.LotteryViews.EditLotteryScreen
import com.example.myapplication.frontEnd.screens.LotteryViews.LotteryInformationScreen
import com.example.myapplication.frontEnd.screens.LotteryViews.LotteryWaitlistScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.campsite.CampsiteLocation
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.data.firebase.NotificationRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.NotificationViews.NotificationsScreen
import com.example.myapplication.frontEnd.screens.NotificationViews.NOTIFICATIONS_ROUTE
import com.example.myapplication.frontEnd.screens.ReservationBookingViews.ReservationCategory
import com.example.myapplication.frontEnd.screens.ReservationBookingViews.ReservationMenuScreen
import com.example.myapplication.frontEnd.screens.ReservationBookingViews.ReservationScreen
import com.example.myapplication.frontEnd.screens.campsiteViews.CampsiteDetailsScreen
import com.example.myapplication.frontEnd.screens.campsiteViews.CampsiteListScreen
import com.example.myapplication.frontEnd.screens.campsiteViews.EditCampsiteScreen
import com.example.myapplication.frontEnd.screens.campsiteViews.RegisterCampsiteScreen
import com.example.myapplication.frontEnd.screens.mainViews.HomeScreen
import com.example.myapplication.frontEnd.screens.mainViews.MainScreen
import com.example.myapplication.frontEnd.screens.mainViews.Signup
import com.example.myapplication.frontEnd.screens.profileViews.AdminProfilesView
import com.example.myapplication.frontEnd.screens.profileViews.ProfileScreen
import com.example.myapplication.frontEnd.screens.MessageViews.ConversationListScreen
import com.example.myapplication.frontEnd.screens.MessageViews.ConversationScreen
import com.example.myapplication.frontEnd.screens.MessageViews.MessageLogScreen
import com.example.myapplication.data.firebase.FeedbackRepository
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.StateFlow

private const val WELCOME_ROUTE = "HomeScreen"
private const val DASHBOARD_ROUTE = "MainScreen"

@Composable
fun CampsiteApp(
    user: User?,
    users: List<User>,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Task<Void>,
    onUpdateUser: (User, User) -> Task<Void>,
    findUser: (String) -> User?,
    getJoinedLotteries: (Map<String, Lottery>) -> List<Lottery>,
    getAllActiveLotteryWaitlists: (Map<String, Lottery>) -> List<Lottery>,
    getLotteryWaitlist: (Lottery?, Map<String, Campsite>) -> Lottery?,
    getAndroidId: (Context) -> String,
    authState: StateFlow<AuthState>,
    campsites: Map<String, Campsite>,
    findCampsite: (String) -> Campsite? = { null },
    onAddCampsite: (Campsite) -> Unit = {},
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String, String) -> Task<Void> =
        { _, _, _, _, _, _ -> Tasks.forResult(null) },
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository,
    lotteryRepository: LotteryRepository,
    notificationRepository: NotificationRepository,
    messageRepository: MessageRepository,
    bookingSchedules: BookingScheduleRepository.Catalog = BookingScheduleRepository.Catalog(),
    retryBookingSchedules: () -> Unit = {},
    feedbackRepository: FeedbackRepository? = null
)  {

    val navController = rememberNavController()
    val currentProfile by rememberUpdatedState(user)
    val currentBookingSchedules by rememberUpdatedState(bookingSchedules)

    NavHost(
        navController = navController,
        startDestination = WELCOME_ROUTE,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(WELCOME_ROUTE) { HomeScreen(navController, authState) }
        composable("Signup") { Signup(navController, onAddUser, getAndroidId) }
        composable(DASHBOARD_ROUTE) {
            val inbox by notificationRepository.inbox.collectAsState()
            MainScreen(
                navController, currentProfile,
                inbox.takeIf { it.userId == currentProfile?.deviceID }?.notifications.orEmpty()
            )
        }
        composable("AdminProfilesView") { AdminProfilesView(navController, users, onDeleteUser) }
        composable("ProfileScreen") {
            ProfileScreen(
                navController,
                currentProfile,
                onUpdateUser,
                onDeleteUser
            )
        }
        composable("CampsiteList") {
            CampsiteListScreen(
                navController,
                user,
                campsites,
                ownedOnly = false,
                bookingSchedules = currentBookingSchedules,
                onRetrySchedules = retryBookingSchedules
            )
        }
        composable("MyCampsiteList") {
            CampsiteListScreen(
                navController,
                user,
                campsites,
                ownedOnly = true,
                bookingSchedules = currentBookingSchedules,
                onRetrySchedules = retryBookingSchedules
            )
        }
        composable("RegisterCampsite") {
            RegisterCampsiteScreen(
                navController,
                user,
                onAddCampsite,
                findCampsite
            )
        }
        composable("EditCampsite/{name}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: ""
            EditCampsiteScreen(navController, user, findCampsite(name), onUpdateCampsite)
        }
        composable("CampsiteDetails/{campsiteName}") { backStackEntry ->
            val campsiteName = backStackEntry.arguments?.getString("campsiteName") ?: ""
            CampsiteDetailsScreen(
                navController = navController,
                campsiteName = campsiteName,
                userProfile = user,
                findCampsite = findCampsite,
                waitingForLotteries = !lotteryRepository.lotteriesLoaded,
                lotteryLoadFailed = lotteryRepository.listenerError != null,
                onJoinWaitlist = lotteryRepository::joinLotteryWaitlist,
                onLeaveWaitlist = { campsiteId, entrant, lotteryId, complete ->
                    lotteryRepository.leaveLotteryWaitlist(campsiteId, entrant,
                        expectedLotteryId = lotteryId, onResult = complete)
                },
                findUser = findUser,
                feedbackRepository = feedbackRepository
            )
        }
        composable("Reservations") {
            ReservationMenuScreen(navController)
        }

        composable("Reservations/{category}") { entry ->
            val category = ReservationCategory.entries.firstOrNull { it.route == entry.arguments?.getString("category") }
            if (category != null) ReservationScreen(
                navController, user, campsites, reservationRepository, scheduleRepository,
                users = users, lotteryRepository = lotteryRepository, category = category
            )
        }

        composable("LotteryInformation") {
            val personalLotteries = getJoinedLotteries(lotteryRepository.allLotteries)
            LotteryInformationScreen(
                user = user,
                lotteries = personalLotteries,
                campsites = campsites,
                onViewWaitlist = { id -> navController.navigate("LotteryWaitlist/${Uri.encode(id)}") },
                onBack = { navController.popBackStack() },
                loadError = lotteryRepository.listenerError?.let { "Could not refresh lottery information. Check your connection." },
                onEditLottery = { id -> navController.navigate("EditLottery/${Uri.encode(id)}") },
                personalJoinedOnly = true,
                onLeaveLottery = { lottery, complete ->
                    if (user == null || lottery.campsiteIds.isEmpty()) complete(false)
                    else lotteryRepository.leaveLotteryWaitlist(lottery.campsiteIds.first(), user,
                        expectedLotteryId = lottery.id, onResult = complete)
                }
            )
        }
        composable("MyCampsiteLotteries") {
            LotteryInformationScreen(
                user = user,
                lotteries = lotteryRepository.getOwnedLotteries(user, campsites),
                campsites = campsites,
                onViewWaitlist = { id -> navController.navigate("LotteryWaitlist/${Uri.encode(id)}") },
                onBack = { navController.popBackStack() },
                loadError = lotteryRepository.listenerError?.let { "Could not refresh lottery information. Check your connection." },
                onEditLottery = { id -> navController.navigate("EditLottery/${Uri.encode(id)}") }
            )
        }
        composable("AllActiveLotteries") {
            val activeLotteries = getAllActiveLotteryWaitlists(lotteryRepository.allLotteries)
            LotteryInformationScreen(
                user = user,
                lotteries = activeLotteries,
                campsites = campsites,
                onViewWaitlist = { id -> navController.navigate("LotteryWaitlist/${Uri.encode(id)}") },
                onBack = { navController.popBackStack() },
                loadError = lotteryRepository.listenerError?.let { "Could not refresh lottery information. Check your connection." },
                onEditLottery = { id -> navController.navigate("EditLottery/${Uri.encode(id)}") },
                showAllActiveLotteries = true
            )
        }
        composable("EditLottery/{lotteryId}") { entry ->
            val selected = lotteryRepository.allLotteries[entry.arguments?.getString("lotteryId")]
            val scheduleId = selected?.bookingSchedule?.id
            var bookingSchedule by remember(scheduleId) { mutableStateOf<BookingSchedule<Lottery>?>(null) }
            var scheduleLoading by remember(scheduleId) { mutableStateOf(scheduleId != null) }
            LaunchedEffect(scheduleRepository, scheduleId) {
                if (scheduleId != null) {
                    scheduleRepository.getSchedule(scheduleId) { schedule ->
                        bookingSchedule = schedule
                        scheduleLoading = false
                    }
                }
            }

            EditLotteryScreen(selected,
                canEdit = selected?.let { LotteryRepository.canEditLotteryListing(user, it, campsites) } == true,
                onSave = { start, end, winners, capacity, complete ->
                    lotteryRepository.updateLotteryFromInput(user, selected, campsites, start, end,
                        winners, capacity, bookingSchedule)
                        .addOnSuccessListener { complete(Result.success(Unit)) }
                        .addOnFailureListener { complete(Result.failure(it)) }
                },
                onBack = { navController.popBackStack() },
                scheduleAvailable = bookingSchedule != null,
                scheduleLoading = scheduleLoading
            )
        }
        composable("LotteryWaitlist/{lotteryId}") { entry ->
            LotteryWaitlistScreen(
                user = user,
                lottery = getLotteryWaitlist(
                    lotteryRepository.allLotteries[entry.arguments?.getString("lotteryId")], campsites
                ),
                campsites = campsites,
                onBack = { navController.popBackStack() },
                loadError = lotteryRepository.listenerError?.let { "Could not refresh the waitlist. Check your connection." }
            )
        }

        composable("CreateLotteryScreen") {
            user?.let { currentUser ->
                CreateLotteryRoute(
                    navController = navController,
                    lotteryRepository = lotteryRepository,
                    user = currentUser,
                    campsites = campsites
                )
            }
        }

        composable(NOTIFICATIONS_ROUTE) {
            val inbox by notificationRepository.inbox.collectAsState()
            val userId = currentProfile?.deviceID
            val returnHome: () -> Unit = {
                if (navController.currentDestination?.route == NOTIFICATIONS_ROUTE) {
                    val home = if (currentProfile == null) WELCOME_ROUTE else DASHBOARD_ROUTE
                    if (!navController.popBackStack(home, inclusive = false)) {
                        navController.navigate(home) {
                            popUpTo(navController.graph.id)
                            launchSingleTop = true
                        }
                    }
                }
            }
            BackHandler(onBack = returnHome)
            NotificationsScreen(
                inbox = inbox.takeIf { userId != null && it.userId == userId },
                onBack = returnHome
            )
        }

        composable("Messages") {
            ConversationListScreen(navController, user, findUser, messageRepository)
        }
        composable("Conversation/{reservationId}") { backStackEntry ->
            val reservationId = backStackEntry.arguments?.getString("reservationId") ?: ""
            ConversationScreen(navController, reservationId, user, findUser, messageRepository)
        }
        composable("MessageLog") {
            MessageLogScreen(navController, user, findUser, messageRepository)
        }
    }
}
