package com.example.myapplication.frontEnd.viewcontroller

import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.CampsiteLocation
import com.example.myapplication.backEnd.firebaseRepositories.AuthState
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.NotificationRepository
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.backEnd.userModels.User
import com.example.myapplication.frontEnd.screens.NotificationViews.NotificationsScreen
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
import kotlinx.coroutines.flow.StateFlow

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
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String, String) -> Unit = { _, _, _, _, _, _ -> },
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository,
    lotteryRepository: LotteryRepository,
    notificationRepository: NotificationRepository
)  {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "HomeScreen",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
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
            if (user?.userType?.trim()?.lowercase() in listOf("admin", "campsite owner")) {
                ReservationMenuScreen(navController)
            } else ReservationScreen(
                navController,
                user,
                campsites,
                reservationRepository,
                scheduleRepository,
                users = users,
                lotteryRepository = lotteryRepository
            )
        }

        composable("Reservations/{category}") { entry ->
            val category = ReservationCategory.entries.firstOrNull { it.route == entry.arguments?.getString("category") }
            if (category != null) ReservationScreen(
                navController, user, campsites, reservationRepository, scheduleRepository,
                users = users, lotteryRepository = lotteryRepository, category = category
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

        composable("NotificationsScreen") {
            user?.let { currentUser ->
                NotificationsScreen(navController, currentUser, notificationRepository)
            }
        }
    }
}
