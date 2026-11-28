package com.example.myapplication.frontEnd.screens.campsiteViews

import com.example.myapplication.core.datetime.AlbertaTime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.navigation.NavController
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.JoinWaitlistResult
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canJoinWaitlist
import com.example.myapplication.frontEnd.screens.LotteryViews.LeaveWaitlistButton
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
import com.example.myapplication.data.firebase.FeedbackRepository
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
 * @param waitingForLotteries True before the shared lottery projection has loaded.
 * @param lotteryLoadFailed True when the shared lottery projection could not refresh.
 * @param onJoinWaitlist Join action pinned to the displayed lottery ID.
 * @param onLeaveWaitlist Leave action pinned to the selected lottery ID by the controller.
 * @param findUser Finds the author of a review by device ID.
 * @param feedbackRepository Loads and saves campsite ratings and reviews.
 */
@Composable
fun CampsiteDetailsScreen(
    navController: NavController,
    campsiteName: String,
    userProfile: User?,
    findCampsite: (String) -> Campsite?,
    waitingForLotteries: Boolean = false,
    lotteryLoadFailed: Boolean = false,
    onJoinWaitlist: ((String, User, String, (JoinWaitlistResult) -> Unit) -> Unit)? = null,
    onLeaveWaitlist: ((String, User, String, (Boolean) -> Unit) -> Unit)? = null,
    findUser: (String) -> User? = { null },
    feedbackRepository: FeedbackRepository? = null
) = AdaptiveContent {

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
        associatedLottery?.id
    ) {
        mutableStateOf(false)
    }

    var leftThisSession by remember(campsiteName, userProfile?.deviceID, associatedLottery?.id) { mutableStateOf(false) }
    var leaveMessage by remember(campsiteName, userProfile?.deviceID, associatedLottery?.id) { mutableStateOf<String?>(null) }
    var joinMessage by remember(campsiteName, userProfile?.deviceID) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(associatedLottery, userProfile?.deviceID) {
        // Clear temporary membership flags once the canonical listener reflects the write.
        val canonicalJoined = associatedLottery?.waitlist?.containsKey(userProfile?.deviceID) == true
        if (!canonicalJoined) leftThisSession = false
        if (canonicalJoined) joinedThisSession = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppLayout.Medium),
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
            feedbackRepository?.let { repository ->
                CampsiteFeedbackSection(
                    campsiteId = campsite.name,
                    user = userProfile,
                    findUser = findUser,
                    feedbackRepository = repository
                )
            }
            /**
             * Only entrants or admins can see the Join Waitlist button.
             */
            // This part of the code should be a controller instead of being in the view part of the code
            // Please fix this later on, so it matches MVC. The view shouldn't instantly update repo.
            // The controller should parse all this, then update the controller. What you have does in
            // fact follow MVC though it does not follow modularity of the code.

            if (isEntrantOrAdmin) {
                Spacer(modifier = Modifier.height(AppLayout.Medium))

                val currentLottery = associatedLottery

                val now = LocalDateTime.now(
                    AlbertaTime.zone
                )

                val alreadyJoined = !leftThisSession && (joinedThisSession ||
                        (
                                currentLottery?.waitlist
                                    ?.contains(userProfile.deviceID) == true
                                ))

                when {

                    lotteryLoadFailed && currentLottery == null -> {
                        Text("Could not load lottery details.", modifier = Modifier.testTag("waitlistStatus"))
                    }

                    waitingForLotteries -> {
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
                        if (onLeaveWaitlist != null) {
                            LeaveWaitlistButton(userProfile, currentLottery,
                                testTag = "leaveWaitlistButton",
                                onLeave = { complete ->
                                    leaveMessage = null
                                    joinMessage = null
                                    onLeaveWaitlist(campsite.name, userProfile, currentLottery.id, complete)
                                },
                                onResult = { success ->
                                    if (success) {
                                        joinedThisSession = false
                                        leftThisSession = true
                                    }
                                    leaveMessage = if (success) "You have left this lottery's waitlist."
                                        else "Could not leave the waitlist. It may have been drawn or changed. Please try again."
                                })
                        }
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

                    !canJoinWaitlist(currentLottery, userProfile.deviceID,
                        currentLottery.campsiteIds.mapNotNull { findCampsite(it)?.ownerID }) -> {
                        Text(
                            "You are not eligible to join this lottery's waitlist.",
                            modifier = Modifier.testTag("waitlistStatus")
                        )
                    }

                    else -> {
                        Button(
                            onClick = {
                                if (!isJoining) {
                                    isJoining = true
                                    joinMessage = null

                                    onJoinWaitlist?.invoke(campsite.name, userProfile, currentLottery.id) { result ->
                                        isJoining = false
                                        if (result == JoinWaitlistResult.JOINED) {
                                            joinedThisSession = true
                                            leftThisSession = false
                                            leaveMessage = null
                                        }
                                        joinMessage = when (result) {
                                            JoinWaitlistResult.JOINED -> "Successfully joined the waitlist."
                                            JoinWaitlistResult.LISTING_CHANGED ->
                                                "This listing changed. Review the current lottery before joining."
                                            JoinWaitlistResult.INELIGIBLE ->
                                                "Could not join. The waitlist may be full, closed, or already joined."
                                            JoinWaitlistResult.FAILED -> "Could not save your entry. Try again."
                                        }
                                    }
                                }
                            },
                            enabled = !isJoining && onJoinWaitlist != null,
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

                if (lotteryLoadFailed && currentLottery != null) {
                    Text("Lottery details could not refresh. Showing the last loaded details.")
                }

                leaveMessage?.let { message ->
                    Spacer(modifier = Modifier.height(AppLayout.Small))
                    Text(message, modifier = Modifier.testTag("waitlistLeaveMessage"))
                }
                // Display the result of joining.
                joinMessage?.let { message ->
                    Spacer(modifier = Modifier.height(AppLayout.Small))

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
                Spacer(modifier = Modifier.height(AppLayout.Medium))

                Button(
                    onClick = {
                        navController.navigate(
                            "EditCampsite/${Uri.encode(campsite.name)}"
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

        Spacer(modifier = Modifier.height(AppLayout.Medium))

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

    Spacer(modifier = Modifier.height(AppLayout.Medium))

    if (photoBitmap != null) {
        CampsitePhoto(photoBitmap, campsite.name)
    } else {
        Text(
            text = "No photo",
            modifier = Modifier.testTag(
                "campsiteDetailsNoPhoto"
            )
        )
    }

    Spacer(modifier = Modifier.height(AppLayout.Medium))

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

@Composable
private fun CampsitePhoto(bitmap: Bitmap, campsiteName: String) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val aspectRatio = image.width.toFloat() / image.height
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Portrait images use a narrower column so that even a tall photo has a bounded height.
        val photoWidth = maxWidth * minOf(aspectRatio, 1f)
        Image(
            bitmap = image,
            contentDescription = "Photo of $campsiteName",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(photoWidth).aspectRatio(aspectRatio).testTag("campsiteDetailsPhoto")
        )
    }
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
            .padding(vertical = AppLayout.Small)
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
