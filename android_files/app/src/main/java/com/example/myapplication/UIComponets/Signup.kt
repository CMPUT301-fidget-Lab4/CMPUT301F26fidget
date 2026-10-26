package com.example.myapplication.UIComponets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.myapplication.Features.User


@Composable
fun Signup(
    navController: NavController,
    onAddUser: (User) -> Unit,
    getAndroidId: () -> String
) {
    var userName by remember { mutableStateOf("") }
    var userPhoneNumber by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)){

            Button(
                onClick = {
                    userType = "entrant"
                }
            ) {
                Text("Entrant")
            }

            Button(
                onClick = {
                    userType = "campsite owner"
                }
            ) {
                Text("Campsite Owner")
            }

            Button(
                onClick = {
                    userType = "admin"
                }
            ) {
                Text("Admin")
            }
        }

        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("Enter Name") },
        )

        OutlinedTextField(
            value = userEmail,
            onValueChange = { userEmail = it },
            label = { Text("Enter Email") },
        )

        OutlinedTextField(
            value = userPhoneNumber,
            onValueChange = { userPhoneNumber = it },
            label = { Text("Enter Phone Number") },
        )

        Button(
            onClick = {
                if (userName.isNotEmpty() && userType.isNotEmpty() && userPhoneNumber.isNotEmpty() && userEmail.isNotEmpty()){
                        val newUser = User(
                            userType,
                            userName,
                            userEmail,
                            userPhoneNumber,
//                            getAndroidId()
                        )
                    onAddUser(newUser)

                    navController.navigate("MainScreen")
                }
            }
        ) {
            Text("Create account")
        }
    }
}
