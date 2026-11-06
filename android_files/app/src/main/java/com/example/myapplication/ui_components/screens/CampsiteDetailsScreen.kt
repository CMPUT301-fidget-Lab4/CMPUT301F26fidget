package com.example.myapplication.ui_components.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.User
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * CampsiteDetailsScreen shows the details of one campsite.
 *
 * It displays the campsite's photo, name, address, description (amenities), maximum occupancy,
 * rating, tags, rules and policies, and whether the campsite is in a lottery. Tags and rules are
 * only shown if the owner filled them in.
 *
 * The campsite is looked up by name each time the screen is drawn, so changes made in Firestore
 * (for example a new rating) show up without reopening the screen. If no campsite has that name,
 * for example because it was removed, a "Campsite not found" message is shown instead.
 *
 * Entrants and admins can join an active campsite lottery. The owner of the campsite sees an
 * Edit button that opens [EditCampsiteScreen].
 *
 * @param navController
 * @param campsiteName The name of the campsite to show, which is also its Firestore document ID.
 * @param findCampsite [CampsiteRepository] method to find a campsite by name.
 */
@Composable
fun CampsiteDetailsScreen(
    navController: NavController,
    campsiteName: String,
    userProfile: User?,
    findCampsite: (String) -> Campsite?,
    joinWaitlist: (String, User, (Boolean) -> Unit) -> Unit = { _, _, onResult -> onResult(false) }
) {
    val campsite = findCampsite(campsiteName)

    var joined by remember(campsiteName, userProfile?.deviceID) {
        mutableStateOf(
            userProfile?.joinedLotteries?.any {
                it.campsiteName == campsiteName
            } == true
        )
    }

    var joinMessage by remember(campsiteName, userProfile?.deviceID) {
        mutableStateOf<String?>(null) }
    var isJoining by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (campsite == null) {
            Text(
                text = "Campsite not found",
                modifier = Modifier.testTag("campsiteNotFound") // for testing
            )
        } else {
            CampsiteDetails(campsite)

            // Only entrants and admins can join.
            // Campsite owners cannot join their own lottery.
            val eligibleUser = userProfile?.takeIf { user ->
                val role = user.userType.trim().lowercase()

                (role == "entrant" || role == "admin") &&
                        user.deviceID != campsite.ownerID
            }

            if (eligibleUser != null && campsite.inLottery) {
                Spacer(modifier = Modifier.height(16.dp))

                if (joined) {
                    Text(
                        text = "You're on the waiting list!",
                        modifier = Modifier.testTag("onWaitlistLabel")
                    )
                } else {
                    Button(
                        enabled = !isJoining,
                        onClick = {
                            isJoining = true
                            joinMessage = null

                            joinWaitlist(campsite.name, eligibleUser) { success ->
                                isJoining = false

                                if (success) {
                                    joined = true
                                    joinMessage = null
                                } else {
                                    joinMessage =
                                        "Could not join the waiting list. " +
                                                "Registration may be closed or the waiting list may be full."
                                }
                            }
                        },
                        modifier = Modifier.testTag("joinWaitlistButton")
                    ) {
                        Text(
                            if (isJoining) "Joining..." else "Join Waiting List"
                        )
                    }
                }

                joinMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        modifier = Modifier.testTag("joinWaitlistMessage")
                    )
                }
            }

            // Existing owner Edit button
            if (userProfile?.deviceID == campsite.ownerID) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        navController.navigate("EditCampsite/${campsite.name}")
                    },
                    modifier = Modifier.testTag("editCampsiteButton")
                ) {
                    Text("Edit Campsite")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromCampsiteDetails") // for testing
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun CampsiteDetails(campsite: Campsite) {
    val photoBitmap = remember(campsite.photo) { decodePhoto(campsite.photo) }

    Text(
        text = campsite.name,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.testTag("campsiteDetailsName") // for testing
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (photoBitmap != null) {
        Image(
            bitmap = photoBitmap.asImageBitmap(),
            contentDescription = "Photo of ${campsite.name}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("campsiteDetailsPhoto") // for testing
        )
    } else {
        Text(
            text = "No photo",
            modifier = Modifier.testTag("campsiteDetailsNoPhoto") // for testing
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    DetailRow(
        label = "Location",
        value = campsite.location?.address?.ifBlank { "Not provided" } ?: "Not provided",
        tag = "campsiteDetailsAddress"
    )

    DetailRow(
        label = "Description (amenities)",
        value = campsite.description.ifBlank { "Not provided" },
        tag = "campsiteDetailsDescription"
    )

    DetailRow(
        label = "Maximum occupancy",
        value = campsite.occupancy.toString(),
        tag = "campsiteDetailsOccupancy"
    )

    DetailRow(
        label = "Rating",
        value = if (campsite.rating > 0.0) "%.1f / 5".format(campsite.rating) else "No ratings yet",
        tag = "campsiteDetailsRating"
    )

    if (campsite.tags.isNotBlank()) {
        DetailRow(
            label = "Tags",
            value = campsite.tags,
            tag = "campsiteDetailsTags"
        )
    }

    if (campsite.termsAndConditions.isNotBlank()) {
        DetailRow(
            label = "Rules and policies",
            value = campsite.termsAndConditions,
            tag = "campsiteDetailsRules"
        )
    }

    DetailRow(
        label = "Lottery",
        value = if (campsite.inLottery) {
            "This campsite is in a lottery"
        } else {
            "This campsite is not in a lottery right now"
        },
        tag = "campsiteDetailsLottery"
    )
}

/**
 * One labelled line of campsite information.
 */
@Composable
private fun DetailRow(label: String, value: String, tag: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)

        Text(text = value, modifier = Modifier.testTag(tag)) // tag is for testing
    }
}

/**
 * Turns the campsite's photo (a base64 string) into a [Bitmap].
 *
 * @return The bitmap, or null if the campsite has no photo or the photo is not a valid image.
 */
private fun decodePhoto(photo: String): Bitmap? {
    if (photo.isEmpty()) {
        return null
    }

    return try {
        val bytes = Base64.decode(photo, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}