package com.example.myapplication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun CampsiteApp(
    users: List<User>,
    onAddUser: (User) -> Unit,
    onDeleteUser: (User) -> Unit,
    onUpdateUser: (User, User) -> Unit,
    findUser : (String) -> User?,
    getAndroidId: (Context) -> String
) {

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, findUser, getAndroidId) }
        composable("Signup") { Signup(navController, onAddUser, getAndroidId) }
        composable("MainScreen") { MainScreen(navController) }
    }
}
