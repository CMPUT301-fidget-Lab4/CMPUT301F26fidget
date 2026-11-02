package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.User
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.testTag

@Composable
fun AdminProfilesView(
    navController: NavController,
    users: List<User>
){
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().testTag("adminProfilesList")
        ) {
            itemsIndexed(users) { index, user ->
                ProfileRow(user = user)

                if (index < users.lastIndex) {
                        HorizontalDivider()
                }
            }
        }

        Button(
            onClick = {
                navController.popBackStack()
            },
            modifier = Modifier.testTag("profilesViewBack")
        ){
            Text("Back to Main Screen")
        }
    }
}

@Composable
fun ProfileRow(user: User) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text("Name: ${user.name}")
        Text("Type: ${user.userType}")
        Text("Email: ${user.email}")
        Text("Phone: ${user.phoneNumber}")
    }
}