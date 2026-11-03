package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * RegisterCampsiteScreen allows a campsite owner to register a new campsite in the app.
 *
 * The owner enters a name, address, description, maximum occupancy, and optional tags.
 * The address is checked with the geocoder & its coordinates are stored with the campsite.
 * The new campsite is saved to Firestore with the current user's device ID as its owner.
 * Only campsite owners and admins can use this screen. Entrants will not be allowed
 * access to this screen, and will get an error message.
 *
 * Image uploading and location picker are seperate cases, and will not be implemented here.
 *
 * @param navController
 * @param userProfile The current user's [User] object.
 * @param onAddCampsite [CampsiteRepository] method to add a campsite to Firestore.
 * @param findCampsite [CampsiteRepository] method to find a campsite by name. Avoid overwriting
 * of existing campsites with the same name.
 *
 */
@Composable
fun RegisterCampsiteScreen(
    navController: NavController,
    userProfile: User?,
    onAddCampsite: (Campsite) -> Unit,
    findCampsite: (String) -> Campsite?
) {
    val canRegister = userProfile != null &&
            (userProfile.userType == "campsite owner" || userProfile.userType == "admin")

    if (userProfile == null || !canRegister) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Only campsite owners can register a campsite")

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("registerCampsiteErrorReturn")
            ) {
                Text("Go Back")
            }
        }
    } else {
        RegisterCampsiteForm(navController, userProfile, onAddCampsite, findCampsite)
    }
}

@Composable
private fun RegisterCampsiteForm(
    navController: NavController,
    userProfile: User,
    onAddCampsite: (Campsite) -> Unit,
    findCampsite: (String) -> Campsite?
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var occupancy by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
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
        Text("Register Campsite")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Campsite Name") },
            modifier = Modifier.testTag("registerCampsiteName")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            modifier = Modifier.testTag("registerCampsiteAddress")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (amenities)") },
            modifier = Modifier.testTag("registerCampsiteDescription")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = occupancy,
            onValueChange = { occupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.testTag("registerCampsiteOccupancy")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Tags (optional)") },
            modifier = Modifier.testTag("registerCampsiteTags")
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(errorMessage, modifier = Modifier.testTag("registerCampsiteError"))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            enabled = !isSaving,
            onClick = {
                val newName = name.trim()
                val newAddress = address.trim()
                val newDescription = description.trim()
                val newOccupancy = occupancy.trim().toIntOrNull()

                if (newName.isEmpty() || newAddress.isEmpty() || newDescription.isEmpty()) {
                    errorMessage = "Fill in the name, address and description"
                } else if (newName.contains("/")) {
                    // name is used as the Firestore document ID, cant contain "/"
                    errorMessage = "The name cannot contain a /"
                } else if (newOccupancy == null || newOccupancy <= 0) {
                    errorMessage = "Maximum occupancy must be a positive number"
                } else if (findCampsite(newName) != null) {
                    errorMessage = "A campsite with that name already exists"
                } else {
                    errorMessage = ""
                    isSaving = true

                    scope.launch {
                        val newLocation = withContext(Dispatchers.IO) {
                            CampsiteLocation(newAddress)
                        }

                        if (!newLocation.isValid) {
                            errorMessage = "Could not find that address"
                            isSaving = false
                        } else {
                            val newCampsite = Campsite(
                                name = newName,
                                location = newLocation,
                                description = newDescription,
                                occupancy = newOccupancy,
                                photo = "",
                                tags = tags.trim(),
                                ownerID = userProfile.deviceID
                            )

                            onAddCampsite(newCampsite)

                            navController.popBackStack()
                        }
                    }
                }
            },
            modifier = Modifier.testTag("registerCampsiteButton")
        ) {
            Text("Register Campsite")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("cancelRegisterCampsite")
        ) {
            Text("Cancel")
        }
    }
}