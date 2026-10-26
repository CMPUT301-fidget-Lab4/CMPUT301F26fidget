package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

@Composable
fun HomeScreen(
    navController: NavController,
    findUser: (String) -> User?,
    getDeviceID: (Context) -> String
) {

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

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                navController.navigate("Signup")
            },
            modifier = Modifier.testTag("welcomeSignUpButton") // for testing
        ) {
            Text("Sign up")
        }

        val userContext: Context = LocalContext.current

        val userDeviceID: String = getDeviceID(userContext)

            if (findUser(userDeviceID) != null){
                navController.navigate("MainScreen")
            }

            else {
                Spacer(modifier = Modifier.height(32.dp))

                Text("Device Account Not Found. Continue To Sign In")
            }
    }
}
