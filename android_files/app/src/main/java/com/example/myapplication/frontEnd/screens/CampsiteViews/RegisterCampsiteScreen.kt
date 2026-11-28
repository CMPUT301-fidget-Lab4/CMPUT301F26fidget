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
        CampsiteFormLayout {
            Text("Only campsite owners can register a campsite")

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
    var name by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var addressNumber by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var streetName by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var city by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var province by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var description by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var occupancy by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var tags by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var policies by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var errorMessage by rememberSaveable(userProfile.deviceID) { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    CampsiteFormLayout {
        Text("Register Campsite", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Campsite Name") },
            modifier = Modifier.fillMaxWidth().testTag("registerCampsiteName")
        )

        CampsiteFieldPair(
            first = { field ->
                OutlinedTextField(
                    value = addressNumber,
                    onValueChange = { addressNumber = it },
                    label = { Text("Address Number") },
                    singleLine = true,
                    modifier = field.testTag("registerCampsiteAddressNumber")
                )
            },
            second = { field ->
                OutlinedTextField(
                    value = streetName,
                    onValueChange = { streetName = it },
                    label = { Text("Street Name") },
                    singleLine = true,
                    modifier = field.testTag("registerCampsiteStreetName")
                )
            }
        )

        CampsiteFieldPair(
            first = { field ->
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = field.testTag("registerCampsiteCity")
                )
            },
            second = { field ->
                OutlinedTextField(
                    value = province,
                    onValueChange = { province = it },
                    label = { Text("Province") },
                    singleLine = true,
                    modifier = field.testTag("registerCampsiteProvince")
                )
            }
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (amenities)") },
            modifier = Modifier.fillMaxWidth().testTag("registerCampsiteDescription")
        )

        OutlinedTextField(
            value = occupancy,
            onValueChange = { occupancy = it },
            label = { Text("Maximum Occupancy") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("registerCampsiteOccupancy")
        )

        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Tags (optional)") },
            modifier = Modifier.fillMaxWidth().testTag("registerCampsiteTags")
        )

        OutlinedTextField(
            value = policies, onValueChange = { policies = it },
            label = { Text("Rules and policies (optional)") },
            supportingText = { Text("List your rules here. You can include https:// links to policies.") },
            minLines = 3, modifier = Modifier.fillMaxWidth().testTag("registerCampsitePolicies")
        )

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, modifier = Modifier.testTag("registerCampsiteError"))
        }

        Button(
            enabled = !isSaving,
            onClick = {
                val newName = name.trim()
                val newAddressNumber = addressNumber.trim()
                val newStreetName = streetName.trim()
                val newCity = city.trim()
                val newProvince = province.trim()
                val newAddress = "$newAddressNumber $newStreetName, $newCity, $newProvince"
                val newDescription = description.trim()
                val newOccupancy = occupancy.trim().toIntOrNull()

                if (newName.isEmpty() || newAddressNumber.isEmpty() || newStreetName.isEmpty() ||
                    newCity.isEmpty() || newProvince.isEmpty() || newDescription.isEmpty()) {
                    errorMessage = "Fill in the name, address, city, province and description"
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
                            CampsiteLocation(newAddress).also { it.lookUpAddress() }
                        }

                        if (!newLocation.isValid) {
                            errorMessage = "Could not find that address. Enter a full street address," +
                                    "e.g 20922 62 Ave NW"
                            isSaving = false
                        } else {
                            val newCampsite = Campsite(
                                name = newName,
                                location = newLocation,
                                description = newDescription,
                                occupancy = newOccupancy,
                                photo = "",
                                tags = tags.trim(),
                                termsAndConditions = policies.trim(),
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

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("cancelRegisterCampsite")
        ) {
            Text("Cancel")
        }
    }
}
