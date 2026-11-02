package com.example.myapplication.ui_components.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.User

/**
 * CampsiteListScreen displays a list of campsites.
 *
 * Entrants use it to view all campsites, whereas Campsite owners use it to view their own
 * campsites. Campsite owners also have a Register new campsite button, which opens the
 * register screen.
 *
 * @param navController
 * @param userProfile: the current user's [User] object.
 * @param campsites: All campsites from [CampsiteRepository].
 * @param ownedOnly: If true, only campsites owned by the current user are shown.
 */

@Composable
fun CampsiteListScreen(
    navController: NavController,
    userProfile: User?,
    campsites: List<Campsite>,
    ownedOnly: Boolean
) {
    val shownCampsites =
        if (ownedOnly) {
            campsites.filter { it.ownerID == userProfile?.deviceID }
        } else {
            campsites.toList()
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Displaying ${shownCampsites.size} campsites")

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shownCampsites) { campsite ->
                // TODO: open the campsite details screen when it exists
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(campsite.name)
                    Text("Capacity ${campsite.occupancy}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (userProfile?.userType == "campsite owner") {
            Button(
                onClick = { navController.navigate("RegisterCampsite") },
                modifier = Modifier.testTag("registerNewCampsite") // for testing
            ) {
                Text("Register a new campsite")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromCampsiteList") // for testing
        ) {
            Text("Back To Main Screen")
        }
    }
}
