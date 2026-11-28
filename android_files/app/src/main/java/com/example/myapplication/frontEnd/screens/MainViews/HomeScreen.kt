package com.example.myapplication.frontEnd.screens.mainViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout


/** Displays account lookup status and signup, or redirects a registered user to MainScreen. */
@Composable
fun HomeScreen(
    navController: NavController,
    authState: StateFlow<AuthState>,
) {
    val currentAuthState by authState.collectAsState()

    LaunchedEffect(currentAuthState) {
        if (currentAuthState is AuthState.LoggedIn) {
            navController.navigate("MainScreen") {
                popUpTo("HomeScreen") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val statusMessage = when (currentAuthState) {
        AuthState.Loading -> null
        AuthState.NotRegistered -> "No account found. Sign up to get started."
        AuthState.Error -> "Unable to check your account. Please try again later."
        // Hide the welcome page while navigation to MainScreen is being processed.
        is AuthState.LoggedIn -> return
    }

    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Welcome to Fidget Campsite Reservation System!",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            if (statusMessage == null) {
                CircularProgressIndicator(modifier = Modifier.testTag("homeLoadingIndicator"))
            } else {
                Text(statusMessage, textAlign = TextAlign.Center)
            }

            if (currentAuthState == AuthState.NotRegistered) {
                Button(
                    onClick = { navController.navigate("Signup") { launchSingleTop = true } },
                    modifier = Modifier.testTag("welcomeSignUpButton")
                ) {
                    Text("Sign up")
                }
            }
        }
    }
}
