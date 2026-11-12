package com.example.myapplication.`Front-End`.Viewcontroller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.`Back-End`.AuthState
import com.example.myapplication.`Back-End`.`Firebase-Repositories`.BookingScheduleRepository
import com.example.myapplication.`Back-End`.UserModels.User
import com.example.myapplication.`Front-End`.screens.ProfileViews.AdminProfilesView
import com.example.myapplication.`Front-End`.screens.MainViews.HomeScreen
import com.example.myapplication.`Front-End`.screens.MainViews.MainScreen
import com.example.myapplication.`Front-End`.screens.ProfileViews.ProfileScreen
import com.example.myapplication.`Front-End`.screens.MainViews.Signup
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.`Back-End`.Campsite
import com.example.myapplication.`Back-End`.CampsiteLocation
import com.example.myapplication.`Back-End`.`Firebase-Repositories`.ReservationRepository
import com.example.myapplication.`Front-End`.screens.CampsiteViews.CampsiteListScreen
import com.example.myapplication.`Front-End`.screens.CampsiteViews.RegisterCampsiteScreen
import com.example.myapplication.`Front-End`.screens.CampsiteViews.CampsiteDetailsScreen
import com.example.myapplication.`Front-End`.screens.CampsiteViews.EditCampsiteScreen
import com.example.myapplication.`Front-End`.screens.`Reservation&BookingViews`.ReservationScreen
import com.example.myapplication.`Back-End`.`Firebase-Repositories`.LotteryRepository
import com.example.myapplication.`Front-End`.screens.LotteryViews.CreateLotteryScreen

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
                CreateLotteryScreen(
                    navController = navController,
                    user = currentUser,
                    campsites = campsites,
                    lotteryRepository = lotteryRepository
                )
            }
        }

    }
}
