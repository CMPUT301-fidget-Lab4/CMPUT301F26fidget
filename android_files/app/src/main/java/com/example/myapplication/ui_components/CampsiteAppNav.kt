package com.example.myapplication.ui_components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.BookingSchedule
import com.example.myapplication.features.BookingScheduleRepository
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.AdminProfilesView
import com.example.myapplication.ui_components.screens.HomeScreen
import com.example.myapplication.ui_components.screens.MainScreen
import com.example.myapplication.ui_components.screens.ProfileScreen
import com.example.myapplication.ui_components.screens.Signup
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.CampsiteLocation
import com.example.myapplication.features.ReservationRepository
import com.example.myapplication.ui_components.screens.CampsiteListScreen
import com.example.myapplication.ui_components.screens.RegisterCampsiteScreen
import com.example.myapplication.ui_components.screens.CampsiteDetailsScreen
import com.example.myapplication.ui_components.screens.EditCampsiteScreen
import com.example.myapplication.ui_components.screens.ReservationScreen

@Composable
fun CampsiteApp(
    user: User?,
    users: List<User>,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUpdateUser: (User, User) -> Unit,
    findUser : (String) -> User?,
    getAndroidId: (Context) -> String,
    authState: StateFlow<AuthState>,
    campsites: Map<String, Campsite>,
    findCampsite: (String) -> Campsite? = { null },
    onAddCampsite: (Campsite) -> Unit = {},
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String) -> Unit = { _, _, _, _, _ -> },
    reservationRepository: ReservationRepository,
    scheduleRepository: BookingScheduleRepository,
    joinWaitlist: (String, User, (Boolean) -> Unit) -> Unit = { _, _, onResult -> onResult(false) }
) {

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
            CampsiteDetailsScreen(navController, campsiteName, user, findCampsite, joinWaitlist)
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
    }
}
