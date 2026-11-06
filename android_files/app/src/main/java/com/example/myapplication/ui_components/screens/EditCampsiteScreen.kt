package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.CampsiteLocation
import com.example.myapplication.features.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditCampsiteScreen(
    navController: NavController,
    userProfile: User?,
    campsite: Campsite?,
    onUpdateCampsite: (String, CampsiteLocation, String, Int, String) -> Unit
) {
    if (userProfile == null || campsite == null || campsite.ownerID != userProfile.deviceID) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("You can only edit your own campsites")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("editCampsiteErrorReturn")
            ) { Text("Go Back") }
        }
        return
    }

    var address by remember { mutableStateOf(campsite.location?.address ?: "") }
    var description by remember { mutableStateOf(campsite.description) }
    var occupancy by remember { mutableStateOf(campsite.occupancy.toString()) }
    var tags by remember { mutableStateOf(campsite.tags) }
    var errorMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Edit ${campsite.name}")
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteAddress")
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Description (amenities)") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteDescription")
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = occupancy, onValueChange = { occupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteOccupancy")
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = tags, onValueChange = { tags = it },
            label = { Text("Tags (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("editCampsiteTags")
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(errorMessage, modifier = Modifier.testTag("editCampsiteError"))
        }
        Spacer(modifier = Modifier.height(16.dp))

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
                        val addressChanged = newAddress != campsite.location?.address
                        val location: CampsiteLocation = if (addressChanged) {
                            withContext(Dispatchers.IO) {
                                CampsiteLocation(newAddress).also { it.lookUpAddress() }
                            }
                        } else {
                            campsite.location ?: CampsiteLocation()
                        }

                        if (addressChanged && !location.isValid) {
                            errorMessage = "Could not find that address"
                            isSaving = false
                        } else {
                            onUpdateCampsite(campsite.name, location, newDescription, newOccupancy, tags.trim())
                            navController.popBackStack()
                        }
                    }
                }
            },
            modifier = Modifier.testTag("saveCampsiteEdits")
        ) { Text("Save Changes") }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("cancelCampsiteEdits")
        ) { Text("Cancel") }
    }
}