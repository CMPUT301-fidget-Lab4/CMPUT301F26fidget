package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import com.example.myapplication.features.AuthState


/**
 * Home screen where the app initially loads. A returning user is automatically redirected to the
 * main screen. A new user presses sign in to create an account.
 *
 * The user's Android ID is fetched. If a Firestore document exists with that ID, the user is
 * redirected to the main screen. Otherwise, the user must create an account. When they press
 * Sign Up they are redirected to the sign-up screen.
 *
 * @param navController
 * @param authState StateFlow for AuthState. Updates function when Firestore fetching finishes on
 * whether the current user is new or retuning.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    authState: StateFlow<AuthState>,
) {
    val currentAuthState by authState.collectAsState()   // recomposes when it changes

    LaunchedEffect(currentAuthState) {
        if (currentAuthState is AuthState.LoggedIn) {
            navController.navigate("MainScreen")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Welcome to Fidget Campsite Reservation System!",
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = {
                navController.navigate("Signup")
            },
            modifier = Modifier.testTag("welcomeSignUpButton") // for testing
        ) {
            Text("Sign up")
        }

        when (val state = currentAuthState) {
            AuthState.Loading       -> CircularProgressIndicator()
            AuthState.NotRegistered -> Text("Device Account Not Found. Continue To Sign In")
            AuthState.Error         -> Text("Something went wrong")
            is AuthState.LoggedIn   -> Text("Welcome back, ${state.user.name}")  // use whatever field User has
        }
    }
}
