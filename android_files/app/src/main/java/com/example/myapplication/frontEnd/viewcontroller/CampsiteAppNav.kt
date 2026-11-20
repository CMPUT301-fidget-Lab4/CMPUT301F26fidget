package com.example.myapplication.frontEnd.viewcontroller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.backEnd.firebaseRepositories.AuthState
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import com.example.myapplication.backEnd.userModels.User
import com.example.myapplication.frontEnd.screens.ProfileViews.AdminProfilesView
import com.example.myapplication.frontEnd.screens.MainViews.HomeScreen
import com.example.myapplication.frontEnd.screens.MainViews.MainScreen
import com.example.myapplication.frontEnd.screens.ProfileViews.ProfileScreen
import com.example.myapplication.frontEnd.screens.MainViews.Signup
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.CampsiteLocation
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.frontEnd.screens.CampsiteViews.CampsiteListScreen
import com.example.myapplication.frontEnd.screens.CampsiteViews.RegisterCampsiteScreen
import com.example.myapplication.frontEnd.screens.CampsiteViews.CampsiteDetailsScreen
import com.example.myapplication.frontEnd.screens.CampsiteViews.EditCampsiteScreen
import com.example.myapplication.frontEnd.screens.ReservationBookingViews.ReservationScreen
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.frontEnd.screens.LotteryViews.CreateLotteryScreen
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.backEnd.featureModels.AppNotification
import com.example.myapplication.backEnd.firebaseRepositories.NotificationRepository
import com.example.myapplication.frontEnd.screens.NotificationViews.NotificationsScreen
import android.Manifest
import kotlinx.coroutines.delay


@Composable
fun CampsiteApp(
    user: User?,
    users: List<User>,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUpdateUser: (User, User) -> Unit,
    findUser: (String) -> User?,
    getAndroidId: (Context) -> String,
    authState: StateFlow<AuthState>,
    campsites: Map<String, Campsite>,
    findCampsite: (String) -> Campsite? = { null },
    onAddCampsite: (Campsite) -> Unit = {},
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String) -> Unit = { _, _, _, _, _ -> },
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository,
    lotteryRepository: LotteryRepository
)  {

    val navController = rememberNavController()

    val context = LocalContext.current
    val notificationRepository = remember { NotificationRepository() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(user?.deviceID) {
        if (user != null && Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(user?.deviceID) {
        val registration = user?.let { u ->
            notificationRepository.listenForUnread(u.deviceID) { n ->
                showNotification(context, n)
                notificationRepository.markRead(n.id)
            }
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(user?.deviceID) {
        if (user != null) {
            while (true) {
                lotteryRepository.drawClosedLotteries()
                delay(30_000)
            }
        }
    }
    NavHost(navController = navController, startDestination = "HomeScreen") {
        composable("HomeScreen") { HomeScreen(navController, authState) }
        composable("Signup") { Signup(navController, onAddUser, getAndroidId) }
        composable("MainScreen") { MainScreen(navController, user) }
        composable("AdminProfilesView") { AdminProfilesView(navController, users, onDeleteUser) }
        composable("ProfileScreen") {
            ProfileScreen(
                navController,
                user,
                onUpdateUser,
                onDeleteUser
            )
        }
        composable("CampsiteList") {
            CampsiteListScreen(
                navController,
                user,
                campsites,
                ownedOnly = false
            )
        }
        composable("MyCampsiteList") {
            CampsiteListScreen(
                navController,
                user,
                campsites,
                ownedOnly = true
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
                lotteryRepository = lotteryRepository
            )
        }
        composable("Reservations") {
            ReservationScreen(
                navController,
                user,
                campsites,
                reservationRepository,
                scheduleRepository
            )
        }

        composable("CreateLotteryScreen") {
            user?.let { currentUser ->
                CreateLotteryFlow(
                    navController = navController,
                    user = currentUser,
                    campsites = campsites,
                    lotteryRepository = lotteryRepository
                )
            }
        }

        composable("NotificationsScreen") {
            user?.let { currentUser ->
                NotificationsScreen(navController, currentUser, notificationRepository)
            }
        }
    }
}

// helper for notification
val NOTIFICATION_CHANNEL_ID = "lottery_results"

fun showNotification(context: Context, n: AppNotification) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(NOTIFICATION_CHANNEL_ID, "Lottery results", NotificationManager.IMPORTANCE_HIGH)
    )
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(if (n.type == "WIN") "You won!" else "Lottery result")
        .setContentText(n.message)
        .setAutoCancel(true)
        .build()
    manager.notify(n.id.hashCode(), notification)
}