package com.example.myapplication.frontEnd.screens.campsiteViews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.features.feedback.Rating
import com.example.myapplication.features.feedback.Review
import com.example.myapplication.data.firebase.FeedbackRepository
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.features.profile.User
import java.time.Instant
import java.time.format.DateTimeFormatter

private val reviewDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Ratings and reviews for one campsite, shown at the bottom of the campsite details screen.
 *
 * - Everyone sees the average rating and every review (US 01.13.02).
 * - An entrant whose stay has started can rate 1–5 stars (US 01.13.01) and
 *   post a review (US 01.13.02), and can change either later.
 * - An admin can remove any review (US 03.10.01) or rating (US 03.09.01),
 *   separately.
 *
 * Uses a plain Column, not a LazyColumn, because the details screen already scrolls.
 *
 * @param campsiteId The campsite's name, which is also its document ID.
 * @param user The logged-in user.
 * @param findUser Looks up a user by device ID, for showing names.
 * @param feedbackRepository Loads and saves ratings and reviews.
 */
@Composable
fun CampsiteFeedbackSection(
    campsiteId: String,
    user: User?,
    findUser: (String) -> User?,
    feedbackRepository: FeedbackRepository
) {
    var reviews by remember(campsiteId) { mutableStateOf<List<Review>>(emptyList()) }
    var ratings by remember(campsiteId) { mutableStateOf<List<Rating>>(emptyList()) }
    var eligible by remember(campsiteId, user?.deviceID) { mutableStateOf(false) }
    var message by remember(campsiteId) { mutableStateOf<String?>(null) }
    var messageIsError by remember(campsiteId) { mutableStateOf(false) }

    // Listen while the section is shown
    DisposableEffect(campsiteId) {
        val reviewListener = feedbackRepository.listenToReviews(campsiteId) { reviews = it }
        val ratingListener = feedbackRepository.listenToRatings(campsiteId) { ratings = it }
        onDispose {
            reviewListener.remove()
            ratingListener.remove()
        }
    }

    // Can this user rate and review here?
    LaunchedEffect(campsiteId, user?.deviceID) {
        if (user != null) {
            feedbackRepository.canGiveFeedback(user, campsiteId) { eligible = it }
        }
    }

    val isAdmin = user?.userType?.trim()?.equals("admin", ignoreCase = true) == true
    val myRating = ratings.firstOrNull { it.entrantId == user?.deviceID }
    val myReview = reviews.firstOrNull { it.entrantId == user?.deviceID }
    val starsByEntrant = ratings.associate { it.entrantId to it.stars }

    // Start the picker and text box from what the user already saved
    var selectedStars by remember(myRating?.stars) { mutableStateOf(myRating?.stars ?: 0) }
    var draft by remember(myReview?.id, myReview?.updatedAt) { mutableStateOf(myReview?.text.orEmpty()) }

    val show: (String?, String) -> Unit = { error, success ->
        message = error ?: success
        messageIsError = error != null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("campsiteFeedback")
    ) {
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("Ratings and reviews", style = MaterialTheme.typography.titleMedium)

        val average = if (ratings.isEmpty()) 0.0 else ratings.map { it.stars }.average()
        Text(
            text = if (ratings.isEmpty()) "No ratings yet"
            else "%.1f / 5 from %d rating%s".format(average, ratings.size, if (ratings.size == 1) "" else "s"),
            modifier = Modifier.testTag("ratingSummary")
        )

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("feedbackMessage")
            )
        }

        // ---- Give a rating and review ----
        if (user != null && eligible) {
            Spacer(Modifier.height(12.dp))
            Text(if (myRating == null) "Rate your stay" else "Your rating", fontWeight = FontWeight.Bold)

            StarPicker(selected = selectedStars, onSelect = { selectedStars = it })

            Button(
                enabled = selectedStars > 0 && selectedStars != myRating?.stars,
                onClick = {
                    feedbackRepository.submitRating(user, campsiteId, selectedStars) { error ->
                        show(error, "Thanks! Your rating was saved.")
                    }
                },
                modifier = Modifier.testTag("submitRatingButton")
            ) {
                Text(if (myRating == null) "Submit rating" else "Update rating")
            }

            Spacer(Modifier.height(12.dp))
            Text(if (myReview == null) "Write a review" else "Your review", fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= Review.MAX_LENGTH) draft = it },
                placeholder = { Text("What was your stay like?") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reviewInput")
            )

            Button(
                enabled = draft.isNotBlank() && draft.trim() != myReview?.text,
                onClick = {
                    feedbackRepository.submitReview(user, campsiteId, draft) { error ->
                        show(error, "Thanks! Your review was posted.")
                    }
                },
                modifier = Modifier.testTag("submitReviewButton")
            ) {
                Text(if (myReview == null) "Post review" else "Update review")
            }
        } else if (user != null && !isAdmin) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You can rate and review this campsite once your stay here has started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("feedbackNotEligible")
            )
        }

        // ---- Everyone's reviews ----
        Spacer(Modifier.height(16.dp))
        Text("Reviews", fontWeight = FontWeight.Bold)

        if (reviews.isEmpty()) {
            Text("No reviews yet.", modifier = Modifier.testTag("noReviews"))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reviews.forEach { review ->
                    ReviewCard(
                        review = review,
                        name = nameOf(review.entrantId, findUser),
                        stars = starsByEntrant[review.entrantId],
                        onRemove = if (isAdmin && user != null) {
                            {
                                feedbackRepository.removeReview(user, review.id) { error ->
                                    show(error, "Review removed.")
                                }
                            }
                        } else null
                    )
                }
            }
        }

        // ---- Admin: every rating, with remove buttons ----
        if (isAdmin && user != null && ratings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("All ratings", fontWeight = FontWeight.Bold)

            ratings.forEach { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adminRatingRow")
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(nameOf(rating.entrantId, findUser))
                        Text(starText(rating.stars), color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(
                        onClick = {
                            feedbackRepository.removeRating(user, rating.id) { error ->
                                show(error, "Rating removed.")
                            }
                        },
                        modifier = Modifier.testTag("removeRatingButton")
                    ) {
                        Text("Remove rating")
                    }
                }
            }
        }
    }
}

/**
 * Five tappable stars. Tapping the third star selects 3.
 */
@Composable
private fun StarPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row {
        for (star in Rating.MIN_STARS..Rating.MAX_STARS) {
            Text(
                text = if (star <= selected) "★" else "☆",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onSelect(star) }
                    .padding(4.dp)
                    .testTag("star_$star")
            )
        }
    }
}

/**
 * One review, with the reviewer's stars if they also rated,
 * and a remove button for admins.
 */
@Composable
private fun ReviewCard(
    review: Review,
    name: String,
    stars: Int?,
    onRemove: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reviewCard")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (stars != null) {
                    Text(starText(stars), color = MaterialTheme.colorScheme.primary)
                }
            }

            Text(review.text)

            val date = Instant.ofEpochMilli(review.updatedAt).atZone(AlbertaTime.zone).format(reviewDateFormat)
            Text(
                text = if (review.edited) "$date (edited)" else date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onRemove != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("removeReviewButton")
                ) {
                    Text("Remove review")
                }
            }
        }
    }
}

private fun starText(stars: Int): String =
    "★".repeat(stars.coerceIn(0, Rating.MAX_STARS)) + "☆".repeat(Rating.MAX_STARS - stars.coerceIn(0, Rating.MAX_STARS))

private fun nameOf(userId: String, findUser: (String) -> User?): String =
    findUser(userId)?.name?.trim()?.ifBlank { null } ?: "Former user"