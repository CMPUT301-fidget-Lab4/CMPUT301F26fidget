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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.User
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete



@Composable
fun AdminProfilesView(
    navController: NavController,
    users: List<User>,
    onDelete: (User) -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("adminProfilesList")

        ) {
            itemsIndexed(users) { index, user ->
                ProfileRow(user = user, onDelete)

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
fun ProfileRow(
    user: User,
    onDelete: (User) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically){
        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("Name: ${user.name}")
            Text("Type: ${user.userType}")
            Text("Email: ${user.email}")
            Text("Phone: ${user.phoneNumber}")
        }

        IconButton(
            onClick = { onDelete(user) },
            modifier = Modifier.testTag("${user.deviceID} delete")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete item",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}