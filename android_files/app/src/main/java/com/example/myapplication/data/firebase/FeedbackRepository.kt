package com.example.myapplication.data.firebase

import android.util.Log
import com.example.myapplication.features.feedback.FeedbackEligibility
import com.example.myapplication.features.feedback.Rating
import com.example.myapplication.features.feedback.RatingTotals
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.features.feedback.Review
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

/**
 * Saves, loads and removes campsite ratings and reviews.
 *
 * Ratings live in "ratings" and reviews in "reviews", one document per
 * entrant per campsite (see [Rating.idFor] and [Review.idFor]).
 *
 * The campsite document keeps ratingTotal, ratingCount and rating (the
 * average). Every rating change updates them in the same transaction, so the
 * average shown on the campsite is always correct.
 *
 * Results are reported as an error message, or null on success, so screens
 * can show it directly (US 01.13.01 asks for an error if a rating can't be saved).
 *
 * @property db The Firestore instance to use.
 */
class FeedbackRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    private val ratingsCollection = db.collection("ratings")
    private val reviewsCollection = db.collection("reviews")
    private val reservationsCollection = db.collection("reservations")
    private val campsitesCollection = db.collection("campsites")

    /** Thrown inside a task chain when the entrant hasn't stayed at the campsite yet. */
    private class NotEligibleException : Exception("Stay has not started")


    // ---------------------------------------------------------------
    // Eligibility
    // ---------------------------------------------------------------

    /**
     * Checks whether a user can rate or review a campsite: they need a
     * reserved stay there that has already started.
     *
     * @param onResult true if they can.
     */
    fun canGiveFeedback(
        user: User,
        campsiteId: String,
        onResult: (Boolean) -> Unit
    ) {
        checkEligible(user, campsiteId)
            .addOnSuccessListener { eligible -> onResult(eligible == true) }
            .addOnFailureListener { onResult(false) }
    }

    private fun checkEligible(user: User, campsiteId: String): Task<Boolean> {
        if (!isValidDocumentId(user.deviceID) || !isValidDocumentId(campsiteId)) {
            return Tasks.forResult(false)
        }

        return reservationsCollection
            .whereEqualTo("entrantId", user.deviceID)
            .whereEqualTo("campsiteId", campsiteId)
            .whereEqualTo("status", ReservationStatus.RESERVED.name)
            .get()
            .continueWith { task ->
                val reservations = task.result.documents.mapNotNull { document ->
                    try {
                        document.toObject(Reservation::class.java)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Skipping unreadable reservation ${document.id}", e)
                        null
                    }
                }
                FeedbackEligibility.hasStayStarted(reservations, user.deviceID, campsiteId)
            }
    }


    // ---------------------------------------------------------------
    // Entrant actions
    // ---------------------------------------------------------------

    /**
     * Saves or changes an entrant's rating, and updates the campsite's average
     * in the same transaction (US 01.13.01).
     *
     * @param stars From 1 to 5.
     * @param onResult null if saved, otherwise a message to show the user.
     */
    fun submitRating(
        user: User,
        campsiteId: String,
        stars: Int,
        onResult: (String?) -> Unit
    ) {
        val rating = Rating.create(campsiteId, user, stars)
        if (rating == null || !isValidDocumentId(campsiteId)) {
            onResult("Choose a rating from ${Rating.MIN_STARS} to ${Rating.MAX_STARS} stars.")
            return
        }

        checkEligible(user, campsiteId)
            .onSuccessTask { eligible ->
                if (eligible != true) {
                    Tasks.forException<Void>(NotEligibleException())
                } else {
                    db.runTransaction<Void> { transaction ->
                        val ratingDocument = ratingsCollection.document(rating.id)
                        val campsiteDocument = campsitesCollection.document(campsiteId)

                        // All reads before any writes
                        val saved = transaction.get(ratingDocument)
                        val campsite = transaction.get(campsiteDocument)
                        check(campsite.exists()) { "Campsite $campsiteId no longer exists." }

                        val oldStars = if (saved.exists()) saved.getLong("stars")?.toInt() else null
                        val totals = readTotals(campsite).replace(oldStars, rating.stars)
                        val toSave = if (saved.exists()) {
                            rating.copy(createdAt = saved.getLong("createdAt") ?: rating.createdAt)
                        } else {
                            rating
                        }

                        transaction.set(ratingDocument, toSave)
                        transaction.update(campsiteDocument, totalsFields(totals))
                        null
                    }
                }
            }
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { error ->
                onResult(messageFor(error, "rating", "You can rate this campsite once your stay there has started."))
            }
    }

    /**
     * Posts or edits an entrant's review (US 01.13.02).
     *
     * @param onResult null if saved, otherwise a message to show the user.
     */
    fun submitReview(
        user: User,
        campsiteId: String,
        text: String,
        onResult: (String?) -> Unit
    ) {
        if (Review.create(campsiteId, user, text) == null || !isValidDocumentId(campsiteId)) {
            onResult("Write a review of up to ${Review.MAX_LENGTH} characters.")
            return
        }

        checkEligible(user, campsiteId)
            .onSuccessTask { eligible ->
                if (eligible != true) {
                    Tasks.forException<Void>(NotEligibleException())
                } else {
                    db.runTransaction<Void> { transaction ->
                        val reviewDocument = reviewsCollection.document(Review.idFor(campsiteId, user.deviceID))
                        val saved = transaction.get(reviewDocument)

                        // Keep the original posting time when editing
                        val review = checkNotNull(
                            Review.create(
                                campsiteId, user, text,
                                createdAt = if (saved.exists()) saved.getLong("createdAt") else null
                            )
                        )
                        transaction.set(reviewDocument, review)
                        null
                    }
                }
            }
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { error ->
                onResult(messageFor(error, "review", "You can review this campsite once your stay there has started."))
            }
    }


    // ---------------------------------------------------------------
    // Admin actions
    // ---------------------------------------------------------------

    /**
     * Removes a rating that violates app policy, and takes it out of the
     * campsite's average in the same transaction (US 03.09.01).
     * The entrant's review, if any, is kept.
     *
     * @param admin The user removing it. Must be an admin.
     * @param onResult null if removed, otherwise a message to show the user.
     */
    fun removeRating(
        admin: User,
        ratingId: String,
        onResult: (String?) -> Unit
    ) {
        if (!isAdmin(admin)) {
            onResult("Only admins can remove ratings.")
            return
        }
        if (!isValidDocumentId(ratingId)) {
            onResult("That rating could not be found.")
            return
        }

        db.runTransaction<Void> { transaction ->
            val ratingDocument = ratingsCollection.document(ratingId)
            val saved = transaction.get(ratingDocument)
            check(saved.exists()) { "That rating was already removed." }

            val campsiteId = saved.getString("campsiteId").orEmpty()
            val oldStars = saved.getLong("stars")?.toInt()
            val campsiteDocument = campsitesCollection.document(campsiteId)
            val campsite = if (isValidDocumentId(campsiteId)) transaction.get(campsiteDocument) else null

            transaction.delete(ratingDocument)
            if (campsite != null && campsite.exists()) {
                val totals = readTotals(campsite).replace(oldStars, null)
                transaction.update(campsiteDocument, totalsFields(totals))
            }
            null
        }
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { error -> onResult(messageFor(error, "rating removal", null)) }
    }

    /**
     * Removes a review that violates app policy (US 03.10.01).
     * The entrant's rating, if any, is kept.
     *
     * @param admin The user removing it. Must be an admin.
     * @param onResult null if removed, otherwise a message to show the user.
     */
    fun removeReview(
        admin: User,
        reviewId: String,
        onResult: (String?) -> Unit
    ) {
        if (!isAdmin(admin)) {
            onResult("Only admins can remove reviews.")
            return
        }
        if (!isValidDocumentId(reviewId)) {
            onResult("That review could not be found.")
            return
        }

        reviewsCollection.document(reviewId).delete()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { error -> onResult(messageFor(error, "review removal", null)) }
    }


    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    /**
     * Listens to a campsite's reviews, most recently updated first.
     *
     * @return Call remove() on this to stop listening.
     */
    fun listenToReviews(
        campsiteId: String,
        onChange: (List<Review>) -> Unit
    ): ListenerRegistration =
        listen(reviewsCollection.whereEqualTo("campsiteId", campsiteId), Review::class.java) { reviews ->
            onChange(reviews.sortedByDescending { it.updatedAt })
        }

    /**
     * Listens to a campsite's ratings, most recently updated first.
     *
     * @return Call remove() on this to stop listening.
     */
    fun listenToRatings(
        campsiteId: String,
        onChange: (List<Rating>) -> Unit
    ): ListenerRegistration =
        listen(ratingsCollection.whereEqualTo("campsiteId", campsiteId), Rating::class.java) { ratings ->
            onChange(ratings.sortedByDescending { it.updatedAt })
        }


    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun <T : Any> listen(
        query: Query,
        type: Class<T>,
        onChange: (List<T>) -> Unit
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                if (error != null) Log.e(TAG, "Could not load ${type.simpleName}s", error)
                return@addSnapshotListener
            }

            // Skip unreadable documents instead of failing the whole list
            val items = snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(type)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Skipping unreadable ${type.simpleName} ${document.id}", e)
                    null
                }
            }
            onChange(items)
        }

    private fun readTotals(campsite: DocumentSnapshot): RatingTotals =
        RatingTotals(
            total = campsite.getLong("ratingTotal") ?: 0,
            count = campsite.getLong("ratingCount") ?: 0
        )

    private fun totalsFields(totals: RatingTotals): Map<String, Any> = mapOf(
        "rating" to totals.average,
        "ratingTotal" to totals.total,
        "ratingCount" to totals.count
    )

    /** Turns a failure into something to show the user, and logs the real cause. */
    private fun messageFor(error: Exception, what: String, notEligibleMessage: String?): String {
        Log.e(TAG, "Could not save $what", error)
        return when {
            error is NotEligibleException && notEligibleMessage != null -> notEligibleMessage
            (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Couldn't connect. Check your internet connection and try again."
            error is IllegalStateException && error.message != null -> error.message!!
            else -> "Could not save your $what. Please try again."
        }
    }

    private fun isAdmin(user: User): Boolean =
        user.userType.trim().equals("admin", ignoreCase = true)

    /** Document IDs must have a nonwhitespace character and no path separator. */
    private fun isValidDocumentId(value: String): Boolean =
        value.isNotBlank() && '/' !in value

    private companion object {
        const val TAG = "Feedback"
    }
}