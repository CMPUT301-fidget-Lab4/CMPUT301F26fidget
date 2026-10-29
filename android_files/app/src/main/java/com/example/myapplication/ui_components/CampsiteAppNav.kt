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
import com.example.myapplication.ui_components.screens.Signup
import kotlinx.coroutines.flow.StateFlow


@Composable
fun CampsiteApp(
    user: User?,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUpdateUser: (User, User) -> Unit,
    findUser : (String) -> User?,
    getAndroidId: (Context) -> String,
    authState: StateFlow<AuthState>
) {

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, authState) }
        composable("Signup") { Signup(navController, onAddUser, getAndroidId) }
        composable("MainScreen") { MainScreen(navController, user) }
    }
}
