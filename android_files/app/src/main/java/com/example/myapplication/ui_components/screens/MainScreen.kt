package com.example.myapplication.ui_components.screens

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
import androidx.navigation.NavController
import com.example.myapplication.features.User

/**
 * Main screen is where the User can navigate to the major features of the app.
 *
 * @param navController
 * @param userProfile The current user's [User] object.
 */
@Composable
fun MainScreen(
    navController: NavController,
    userProfile: User?
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        if (userProfile != null){

            Text("Welcome ${userProfile.name}!")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    return@Button
                }
            ) {
                Text("Search for campsites")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    return@Button
                }
            ) {
                Text("View Lottery Information")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    return@Button
                }
            ) {
                Text("View Reservation Dates")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    return@Button
                }
            ) {
                Text("View Messages")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    navController.navigate("ProfileScreen")
                },
                modifier = Modifier.testTag("ProfileScreen")
            ) {
                Text("Profile")
            }
        }
    }
}