package com.example.myapplication.frontEnd.screens.profileViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import com.example.myapplication.features.profile.User
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
import com.google.android.gms.tasks.Task

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
 * @param onUpdateUser writes the profile's editable fields
 * @param onDeleteUser deletes the profile and its related records
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    userProfile: User?,
    onUpdateUser: (User, User) -> Task<Void>,
    onDeleteUser: (User) -> Task<Void>
) {
    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        if (userProfile == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppLayout.Medium),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Profile Display Error")

                Button(
                    onClick = { navController.navigate("MainScreen") { launchSingleTop = true } },
                    modifier = Modifier.testTag("profileErrorReturn")
                ) {
                    Text("Return to Main Screen")
                }
            }
            return@AdaptiveContent
        }

        var editing by remember(userProfile.deviceID) { mutableStateOf(false) }
        var editBase by remember(userProfile.deviceID) { mutableStateOf(userProfile) }
        var displayUserName by remember(userProfile.deviceID) { mutableStateOf(userProfile.name) }
        var displayUserEmail by remember(userProfile.deviceID) { mutableStateOf(userProfile.email) }
        var displayUserPhone by remember(userProfile.deviceID) { mutableStateOf(userProfile.phoneNumber) }
        var alertsEnabled by remember(userProfile.deviceID) { mutableStateOf(userProfile.lotteryAlertsEnabled) }
        var saving by remember(userProfile.deviceID) { mutableStateOf(false) }
        var saveError by remember(userProfile.deviceID) { mutableStateOf<String?>(null) }
        var showDeleteDialog by remember(userProfile.deviceID) { mutableStateOf(false) }
        var deleting by remember(userProfile.deviceID) { mutableStateOf(false) }
        var deletionError by remember(userProfile.deviceID) { mutableStateOf<String?>(null) }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("User Profile", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = userProfile.userType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Account Type") },
                modifier = Modifier.fillMaxWidth().testTag("profileUserType")
            )

            OutlinedTextField(
                value = if (editing) displayUserName else userProfile.name,
                onValueChange = { displayUserName = it },
                label = { Text("Name") },
                readOnly = !editing || saving,
                modifier = Modifier.fillMaxWidth().testTag("profileName")
            )

            OutlinedTextField(
                value = if (editing) displayUserEmail else userProfile.email,
                onValueChange = { displayUserEmail = it },
                label = { Text("Email") },
                readOnly = !editing || saving,
                modifier = Modifier.fillMaxWidth().testTag("profileEmail")
            )

            OutlinedTextField(
                value = if (editing) displayUserPhone else userProfile.phoneNumber,
                onValueChange = { displayUserPhone = it },
                label = { Text("Phone Number") },
                readOnly = !editing || saving,
                modifier = Modifier.fillMaxWidth().testTag("profilePhoneNumber")
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lottery result alerts", modifier = Modifier.weight(1f))
                    Switch(
                        checked = if (editing) alertsEnabled else userProfile.lotteryAlertsEnabled,
                        onCheckedChange = { alertsEnabled = it },
                        enabled = editing && !saving,
                        modifier = Modifier.testTag("lotteryAlertsEnabled")
                    )
                }
                Text(
                    "Lottery results are always available in Notifications.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!editing) {
                Button(
                    onClick = {
                        editBase = userProfile
                        displayUserName = userProfile.name
                        displayUserEmail = userProfile.email
                        displayUserPhone = userProfile.phoneNumber
                        alertsEnabled = userProfile.lotteryAlertsEnabled
                        editing = true
                    },
                    modifier = Modifier.testTag("editProfile")
                ) {
                    Text("Edit Profile")
                }

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

                if (saveError != null) {
                    Text(saveError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        if (displayUserName.isNotEmpty() && displayUserEmail.isNotEmpty() && displayUserPhone.isNotEmpty()) {

                            val updatedUser = editBase.copy(
                                name = displayUserName,
                                email = displayUserEmail,
                                phoneNumber = displayUserPhone,
                                lotteryAlertsEnabled = alertsEnabled
                            )

                            saving = true
                            saveError = null
                            onUpdateUser(updatedUser, editBase).addOnCompleteListener { task ->
                                saving = false
                                if (task.isSuccessful) {
                                    editing = false
                                } else {
                                    saveError = "Could not save profile changes. Try again."
                                }
                            }
                        }

                        else {
                            saveError = "Fill all fields to save changes"
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.testTag("saveProfileEdits")
                ) {
                    Text(if (saving) "Saving…" else "Save Changes")
                }

                Button(
                    onClick = {
                        editing = false
                        saveError = null
                    },
                    enabled = !saving,
                    modifier = Modifier.testTag("cancelProfileChanges")
                ) {
                    Text("Cancel Changes")
                }
            }

            Button(
                onClick = {
                    //TODO Implement app guidelines
                },
                modifier = Modifier.testTag("appGuidelines")
            ) {
                Text("App Policy & Guidelines")
            }

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
                    text = {
                        Text(
                            deletionError ?: "This action cannot be undone.",
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deleting = true
                                deletionError = null
                                onDeleteUser(userProfile).addOnCompleteListener { task ->
                                    deleting = false
                                    if (task.isSuccessful) {
                                        navController.navigate("HomeScreen") {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        deletionError = task.exception?.message ?: "Could not delete profile. Try again."
                                    }
                                }
                            },
                            enabled = !deleting,
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
