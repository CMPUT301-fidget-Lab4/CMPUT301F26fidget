package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.User
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton

/**
 * ProfileScreen allows the user to view and update their profile.
 *
 * Profile screen displays the user's user type, name, email, and phone number. A user can
 * edit or delete their profile which updates Firestore. Deleting your profile redirects
 * you to the home screen where you have to sign up for a new profile.
 * If the passed [User] object is null, the screen displays an error message and prompts the user
 * to return to the Main Screen
 *
 * @param navController
 * @param userProfile The current user's [User] object
 * @param onUpdateUser [UserRepository] method to update a user's profile in Firestore
 * @param onDeleteUser [UserRepository] method to delete a user's profile from Firestore
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    userProfile: User?,
    onUpdateUser: (User, User) -> Unit,
    onDeleteUser: (User) -> Unit
) {

    if (userProfile == null){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Profile Display Error")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    navController.navigate("MainScreen")
                },
                modifier = Modifier.testTag("profileErrorReturn") // for testing
            ) {
                Text("Return to Main Screen")
            }
        }
    }

    else {

        var isEditing by remember { mutableStateOf(true) }
        var userProfileState by remember { mutableStateOf(userProfile) }
        var displayUserType by remember { mutableStateOf(userProfileState.userType) }
        var displayUserName by remember { mutableStateOf(userProfileState.name)}
        var displayUserEmail by remember { mutableStateOf(userProfileState.email)}
        var displayUserPhone by remember { mutableStateOf(userProfileState.phoneNumber)}
        var showDeleteDialog by remember { mutableStateOf(false) }
        var incompleteChanges by remember { mutableStateOf(false) }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("User Profile")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = userProfile.userType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account Type") },
                modifier = Modifier.testTag("profileUserType")
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = displayUserName,
                onValueChange = { displayUserName = it },
                label = { Text("Name") },
                readOnly = isEditing,
                modifier = Modifier.testTag("profileName")
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = displayUserEmail,
                onValueChange = { displayUserEmail = it },
                label = { Text("Email") },
                readOnly = isEditing,
                modifier = Modifier.testTag("profileEmail")
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = displayUserPhone,
                onValueChange = { displayUserPhone = it },
                label = { Text("Phone Number") },
                readOnly = isEditing,
                modifier = Modifier.testTag("profilePhoneNumber")
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isEditing) {
                Button(
                    onClick = {
                        isEditing = !isEditing
                    },
                    modifier = Modifier.testTag("editProfile")
                ) {
                    Text("Edit Profile")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showDeleteDialog = true
                    },
                    modifier = Modifier.testTag("deleteProfile")
                ) {
                    Text("Delete Profile")
                }
            }

            else {

                if (incompleteChanges) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Fill all fields to save changes")

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        if (displayUserName.isNotEmpty() && displayUserEmail.isNotEmpty() && displayUserPhone.isNotEmpty()) {

                            isEditing = !isEditing

                            val updatedUser = User(
                                displayUserType,
                                displayUserName,
                                displayUserEmail,
                                displayUserPhone,
                                userProfile.deviceID
                            )

                            onUpdateUser(updatedUser, userProfile)

                            userProfileState = updatedUser

                            incompleteChanges = false
                        }

                        else {
                           incompleteChanges = true
                        }
                    },
                    modifier = Modifier.testTag("saveProfileEdits")
                ) {
                    Text("Save Changes")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isEditing = !isEditing

                        displayUserName = userProfileState.name
                        displayUserEmail = userProfileState.email
                        displayUserPhone = userProfileState.phoneNumber

                        incompleteChanges = false
                    },
                    modifier = Modifier.testTag("cancelProfileChanges")
                ) {
                    Text("Cancel Changes")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    //TODO Implement app guidelines
                },
                modifier = Modifier.testTag("appGuidelines")
            ) {
                Text("App Policy & Guidelines")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.testTag("returnMainScreen")
            ) {
                Text("Back To Main Screen")
            }

            if (showDeleteDialog){

                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Profile?") },
                    text = { Text("This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteUser(userProfile)
                                navController.navigate("HomeScreen")
                            },
                            modifier = Modifier.testTag("confirmDeleteProfile")
                            ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.testTag("cancelDeleteProfile")
                            ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}