package com.example.myapplication.frontEnd.screens.campsiteViews

import com.example.myapplication.backEnd.time.AlbertaTime

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.FeedbackRepository
import com.example.myapplication.frontEnd.screens.CampsiteViews.CampsiteFeedbackSection
import com.example.myapplication.backEnd.userModels.User
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.time.LocalDateTime

/**
 * Displays the details of a campsite.
 *
 * Entrants can join a campsite's lottery waitlist
 * while registration is open.
 *
 * Campsite owners can edit their campsite.
 *
 * @param navController Navigation controller.
 * @param campsiteName Name of the campsite.
 * @param userProfile Currently signed-in user.
 * @param findCampsite Finds the campsite by name.
 * @param lotteryRepository Handles lottery operations.
 */
@Composable
fun CampsiteDetailsScreen(
    navController: NavController,
    campsiteName: String,
    userProfile: User?,
    findCampsite: (String) -> Campsite?,
    lotteryRepository: LotteryRepository? = null,
    findUser: (String) -> User?,
    feedbackRepository: FeedbackRepository
) {

    val campsite = findCampsite(campsiteName)
    val associatedLotteries = campsite?.lotteryCollection.orEmpty()
        .filter { campsiteName in it.campsiteIds }
    val associatedLottery = associatedLotteries.filterNot { it.drawn }
        .maxByOrNull { it.period.registrationStart }
        ?: associatedLotteries.maxByOrNull { it.period.registrationStart }

    val isEntrantOrAdmin = userProfile?.userType
        ?.trim()
        ?.let {
            it.equals("entrant", ignoreCase = true) ||
                    it.equals("admin", ignoreCase = true)
        } == true

    var lottery by remember(campsiteName, campsite, associatedLottery) {
        mutableStateOf(associatedLottery)
    }

    var loadingLottery by remember(campsiteName) {
        mutableStateOf(false)
    }

    var isJoining by remember(campsiteName) {
        mutableStateOf(false)
    }

    var joinedThisSession by remember(
        campsiteName,
        userProfile?.deviceID,
        lottery?.id
    ) {
        mutableStateOf(false)
    }

    var joinMessage by remember(campsiteName) {
        mutableStateOf<String?>(null)
    }

    /**
     * Start with the campsite's matching lottery and refresh its saved status.
     * The campsite flag does not determine whether a waitlist can be joined.
     */
    LaunchedEffect(
        campsiteName,
        campsite,
        associatedLottery,
        isEntrantOrAdmin,
        userProfile?.deviceID,
        lotteryRepository
    ) {
        lottery = associatedLottery
        joinMessage = null


        if (
            isEntrantOrAdmin &&
            campsite != null &&
            lotteryRepository != null
        ) {
            loadingLottery = true

            val currentLottery = suspendCancellableCoroutine<Lottery?> { continuation ->
                lotteryRepository.getLottery(campsiteName) { savedLottery ->
                    if (continuation.isActive) continuation.resume(savedLottery)
                }
            }
            lottery = currentLottery?.takeIf { campsiteName in it.campsiteIds }
            loadingLottery = false
        } else {
            loadingLottery = false
        }
    }

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
                modifier = Modifier.testTag("campsiteNotFound")
            )
        } else {

            // Display existing campsite information.
            CampsiteDetails(campsite)
            CampsiteFeedbackSection(
                campsiteId = campsite.name,
                user = userProfile,
                findUser = findUser,
                feedbackRepository = feedbackRepository
            )
            /**
             * Only entrants or admins can see the Join Waitlist button.
             */
            // This part of the code should be a controller instead of being in the view part of the code
            // Please fix this later on, so it matches MVC. The view shouldn't instantly update repo.
            // The controller should parse all this, then update the controller. What you have does in
            // fact follow MVC though it does not follow modularity of the code.

            if (isEntrantOrAdmin) {
                Spacer(modifier = Modifier.height(16.dp))

                val currentLottery = lottery

                val now = LocalDateTime.now(
                    AlbertaTime.zone
                )

                val alreadyJoined = joinedThisSession ||
                        (
                                currentLottery?.waitlist
                                    ?.contains(userProfile.deviceID) == true
                                )

                when {

                    loadingLottery -> {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag(
                                "lotteryLoadingIndicator"
                            )
                        )
                    }

                    currentLottery == null -> {
                        Text(
                            text = "Lottery details are unavailable.",
                            modifier = Modifier.testTag("waitlistStatus")
                        )
                    }

                    currentLottery.drawn -> {
                        Text(
                            text = "The lottery has already been drawn.",
                            modifier = Modifier.testTag("waitlistStatus")
                        )
                    }

                    alreadyJoined -> {
                        Text(
                            text = "You are on the waitlist.",
                            modifier = Modifier.testTag("alreadyOnWaitlist")
                        )
                    }

                    !currentLottery.period.registrationIsOpen(now) -> {
                        val statusMessage =
                            if (
                                now.isBefore(
                                    currentLottery.period.registrationStart
                                )
                            ) {
                                "Waitlist registration has not opened yet."
                            } else {
                                "Waitlist registration has closed."
                            }

                        Text(
                            text = statusMessage,
                            modifier = Modifier.testTag("waitlistStatus")
                        )
                    }

                    currentLottery.waitlistCapacity > 0 &&
                            currentLottery.waitlist.size >=
                            currentLottery.waitlistCapacity -> {
                        Text(
                            text = "The waitlist is full.",
                            modifier = Modifier.testTag("waitlistStatus")
                        )
                    }

                    //
                    else -> {
                        Button(
                            onClick = {
                                if (!isJoining) {
                                    isJoining = true
                                    joinMessage = null

                                    lotteryRepository?.let { repository ->

                                        // Join the waitlist in Firestore.
                                        repository.joinLotteryWaitlist(
                                            campsiteName = campsite.name,
                                            user = userProfile
                                        ) { success ->

                                            isJoining = false

                                            if (success) {
                                                joinedThisSession = true

                                                joinMessage =
                                                    "Successfully joined the waitlist."
                                            } else {
                                                joinMessage =
                                                    "Could not join the waitlist. It may be full, closed, or already joined."
                                            }

                                            // Refresh the actual lottery.
                                            // This does not attempt another join.
                                            repository.getLottery(
                                                campsite.name
                                            ) { updated ->
                                                lottery = updated
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isJoining && lotteryRepository != null,
                            modifier = Modifier.testTag(
                                "joinWaitlistButton"
                            )
                        ) {
                            Text(
                                text = if (isJoining) {
                                    "Joining..."
                                } else {
                                    "Join Waitlist"
                                }
                            )
                        }
                    }
                }

                // Display the result of joining.
                joinMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message,
                        modifier = Modifier.testTag(
                            "waitlistJoinMessage"
                        )
                    )
                }
            }

            /**
             * Keep the Edit Campsite button for the owner.
             */
            if (userProfile?.deviceID == campsite.ownerID) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        navController.navigate(
                            "EditCampsite/${campsite.name}"
                        )
                    },
                    modifier = Modifier.testTag(
                        "editCampsiteButton"
                    )
                ) {
                    Text("Edit Campsite")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                navController.popBackStack()
            },
            modifier = Modifier.testTag(
                "returnFromCampsiteDetails"
            )
        ) {
            Text("Back")
        }
    }
}

/**
 * Displays an individual campsite's details.
 */
@Composable
private fun CampsiteDetails(campsite: Campsite) {

    val photoBitmap = remember(campsite.photo) {
        decodePhoto(campsite.photo)
    }

    Text(
        text = campsite.name,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.testTag("campsiteDetailsName")
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
                .testTag("campsiteDetailsPhoto")
        )
    } else {
        Text(
            text = "No photo",
            modifier = Modifier.testTag(
                "campsiteDetailsNoPhoto"
            )
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    DetailRow(
        label = "Location",
        value = campsite.location?.address
            ?.ifBlank { "Not provided" }
            ?: "Not provided",
        tag = "campsiteDetailsAddress"
    )

    DetailRow(
        label = "Description (amenities)",
        value = campsite.description.ifBlank {
            "Not provided"
        },
        tag = "campsiteDetailsDescription"
    )

    DetailRow(
        label = "Maximum occupancy",
        value = campsite.occupancy.toString(),
        tag = "campsiteDetailsOccupancy"
    )

    DetailRow(
        label = "Rating",
        value = if (campsite.rating > 0.0) {
            "%.1f / 5".format(campsite.rating)
        } else {
            "No ratings yet"
        },
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
 * Displays one labelled campsite detail.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            modifier = Modifier.testTag(tag)
        )
    }
}


//  This should go into the image class. It is not the role of the CampsiteScreen.kt to
//  decode the image though there is a image class which you can just place this as function.
//  THough we should develop this in milestone 2 .
/**
 * Converts a Base64 photo string into a Bitmap.
 *
 * @return Decoded Bitmap, or null if invalid.
 */
private fun decodePhoto(photo: String): Bitmap? {

    if (photo.isEmpty()) {
        return null
    }

    return try {
        val bytes = Base64.decode(
            photo,
            Base64.DEFAULT
        )

        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    } catch (_: Exception) {
        null
    }
}
