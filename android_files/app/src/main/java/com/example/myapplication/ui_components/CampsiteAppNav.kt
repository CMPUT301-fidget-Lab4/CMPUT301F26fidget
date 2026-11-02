package com.example.myapplication.ui_components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.HomeScreen
import com.example.myapplication.ui_components.screens.MainScreen
import com.example.myapplication.ui_components.screens.ProfileScreen
import com.example.myapplication.ui_components.screens.Signup
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.features.Campsite
import com.example.myapplication.ui_components.screens.CampsiteListScreen
import com.example.myapplication.ui_components.screens.RegisterCampsiteScreen

@Composable
fun CampsiteApp(
    user: User?,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUpdateUser: (User, User) -> Unit,
    findUser : (String) -> User?,
    getAndroidId: (Context) -> String,
    authState: StateFlow<AuthState>,
    campsites: List<Campsite> = emptyList(),
    findCampsite: (String) -> Campsite? = { null },
    onAddCampsite: (Campsite) -> Unit = {}
) {

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "HomeScreen") {
        composable("HomeScreen") { HomeScreen(navController, authState) }
        composable("Signup") { Signup(navController, onAddUser, getAndroidId) }
        composable("MainScreen") { MainScreen(navController, user) }
        composable("ProfileScreen") { ProfileScreen(navController, user, onUpdateUser, onDeleteUser) }
        composable("CampsiteList") { CampsiteListScreen(navController, user, campsites, ownedOnly = false) }
        composable("MyCampsiteList") { CampsiteListScreen(navController, user, campsites, ownedOnly = true) }
        composable("RegisterCampsite") { RegisterCampsiteScreen(navController, user, onAddCampsite, findCampsite) }
    }
}
