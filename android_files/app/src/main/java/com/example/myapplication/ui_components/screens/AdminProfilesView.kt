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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue


@Composable
fun AdminProfilesView(
    navController: NavController,
    users: List<User>,
    onDeleteUser: (User) -> Unit
){

    var userToDelete by remember { mutableStateOf<User?>(null) }

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
                ProfileRow(user = user, onDeleteClick = { userToDelete = user})

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

    if (userToDelete != null) {

        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete Profile?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteUser(userToDelete!!)
                        userToDelete = null
                    },
                    modifier = Modifier.testTag("confirmUserDelete")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { userToDelete = null },
                    modifier = Modifier.testTag("cancelUserDelete")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileRow(
    user: User,
    onDeleteClick: () -> Unit
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
            onClick = { onDeleteClick() },
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