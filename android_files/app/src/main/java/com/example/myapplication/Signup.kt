package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext



@Composable
fun Signup(
    navController: NavController,
    onAddUser: (User) -> Unit,
    getAndroidId: (Context) -> String
) {
    var userName by remember { mutableStateOf("") }
    var userPhoneNumber by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf("") }
    var incompleteEntry by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val userContext: Context = LocalContext.current

        val userDeviceID: String = getAndroidId(userContext)

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)){

            Button(
                onClick = {
                    userType = "entrant"
                }
            ) {
                Text("Entrant")
            }


            Spacer(modifier = Modifier.width(7.dp))

            Button(
                onClick = {
                    userType = "campsite owner"
                }
            ) {
                Text("Campsite Owner")
            }

            Spacer(modifier = Modifier.width(7.dp))

            Button(
                onClick = {
                    userType = "admin"
                }
            ) {
                Text("Admin")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("Enter Name") },
        )


        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userEmail,
            onValueChange = { userEmail = it },
            label = { Text("Enter Email") },
        )


        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userPhoneNumber,
            onValueChange = { userPhoneNumber = it },
            label = { Text("Enter Phone Number") },
        )


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (userName.isNotEmpty() && userType.isNotEmpty() && userPhoneNumber.isNotEmpty() && userEmail.isNotEmpty()){

                        val newUser = User(
                            userType,
                            userName,
                            userEmail,
                            userPhoneNumber,
                            userDeviceID
                        )

                    onAddUser(newUser)

                    navController.navigate("MainScreen")
                }

                else {
                    incompleteEntry = true
                }
            }
        ) {
            Text("Create account")
        }


        if (incompleteEntry){
            Spacer(modifier = Modifier.height(16.dp))

            Text("Complete all fields to sign up")
        }
    }
}
