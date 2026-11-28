package com.example.myapplication.frontEnd.screens.campsiteViews

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.campsite.CampsiteLocation
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun EditCampsiteScreen(
    navController: NavController,
    userProfile: User?,
    campsite: Campsite?,
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String, String) -> Task<Void>
) {
    if (userProfile == null || campsite == null || campsite.ownerID != userProfile.deviceID) {
        CampsiteFormLayout {
            Text("You can only edit your own campsites")
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("editCampsiteErrorReturn")
            ) { Text("Go Back") }
        }
        return
    }

    var address by rememberSaveable(campsite.name) { mutableStateOf(campsite.location?.address ?: "") }
    var description by rememberSaveable(campsite.name) { mutableStateOf(campsite.description) }
    var occupancy by rememberSaveable(campsite.name) { mutableStateOf(campsite.occupancy.toString()) }
    var tags by rememberSaveable(campsite.name) { mutableStateOf(campsite.tags) }
    var policies by rememberSaveable(campsite.name) { mutableStateOf(campsite.termsAndConditions) }
    var errorMessage by rememberSaveable(campsite.name) { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    CampsiteFormLayout {
        Text("Edit ${campsite.name}", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteAddress")
        )
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Description (amenities)") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteDescription")
        )
        OutlinedTextField(
            value = occupancy, onValueChange = { occupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteOccupancy")
        )
        OutlinedTextField(
            value = tags, onValueChange = { tags = it },
            label = { Text("Tags (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteTags")
        )

        OutlinedTextField(
            value = policies, onValueChange = { policies = it },
            label = { Text("Rules and policies (optional)") },
            supportingText = { Text("List your rules here. You can include https:// links to policies.") },
            minLines = 3, modifier = Modifier.fillMaxWidth().testTag("editCampsitePolicies")
        )

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, modifier = Modifier.testTag("editCampsiteError"))
        }

        Button(
            enabled = !isSaving,
            onClick = {
                val newAddress = address.trim()
                val newDescription = description.trim()
                val newOccupancy = occupancy.trim().toIntOrNull()

                if (newAddress.isEmpty() || newDescription.isEmpty()) {
                    errorMessage = "Fill in the address and description"
                } else if (newOccupancy == null || newOccupancy <= 0) {
                    errorMessage = "Maximum occupancy must be a positive number"
                } else {
                    errorMessage = ""
                    isSaving = true
                    scope.launch {
                        try {
                            val addressChanged = newAddress != campsite.location?.address
                            val location = if (addressChanged) {
                                withContext(Dispatchers.IO) {
                                    CampsiteLocation(newAddress).also { it.lookUpAddress() }
                                }
                            } else {
                                campsite.location ?: CampsiteLocation()
                            }

                            if (addressChanged && !location.isValid) {
                                errorMessage = "Could not find that address"
                                return@launch
                            }
                            onUpdateCampsite(
                                campsite.name, location, newDescription, newOccupancy, tags.trim(), policies.trim()
                            ).await()
                            navController.popBackStack()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "Could not save the campsite. Try again."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            modifier = Modifier.testTag("saveCampsiteEdits")
        ) { Text("Save Changes") }

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("cancelCampsiteEdits")
        ) { Text("Cancel") }
    }
}
