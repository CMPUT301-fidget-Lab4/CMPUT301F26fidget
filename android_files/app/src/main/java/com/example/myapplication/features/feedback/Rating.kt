package com.example.myapplication.features.feedback

import com.example.myapplication.features.profile.User

/**
 * One entrant's star rating of one campsite (US 01.13.01).
 *
 * Each entrant has at most one rating per campsite. The document ID is
 * "<campsiteId>_<entrantId>", so rating again replaces the old rating
 * instead of adding a second one.
 *
 * Every field has a default so Firestore's toObject() works.
 *
 * @param id Firestore document id, see [idFor].
 * @param campsiteId The campsite being rated.
 * @param entrantId Device ID of the entrant who rated it.
 * @param stars From 1 to 5.
 * @param createdAt When it was first given, in milliseconds.
 * @param updatedAt When it was last changed, in milliseconds.
 */
data class Rating(
    var id: String = "",
    var campsiteId: String = "",
    var entrantId: String = "",
    var stars: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = createdAt
) {
    companion object {
        const val MIN_STARS = 1
        const val MAX_STARS = 5

        /** The document ID for an entrant's rating of a campsite. */
        fun idFor(campsiteId: String, entrantId: String): String = "${campsiteId}_$entrantId"

        /** true if the number of stars is allowed. */
        fun isValidStars(stars: Int): Boolean = stars in MIN_STARS..MAX_STARS

        /**
         * Builds a rating after checking the stars and IDs.
         * Whether the entrant has stayed at the campsite is checked separately,
         * see [FeedbackEligibility].
         *
         * @return The rating, or null if the input is invalid.
         */
        fun create(
            campsiteId: String,
            entrant: User,
            stars: Int,
            now: Long = System.currentTimeMillis()
        ): Rating? {
            if (campsiteId.isBlank() || entrant.deviceID.isBlank() || !isValidStars(stars)) {
                return null
            }
            return Rating(
                id = idFor(campsiteId, entrant.deviceID),
                campsiteId = campsiteId,
                entrantId = entrant.deviceID,
                stars = stars,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

/**
 * The running sum and count of a campsite's ratings.
 *
 * Stored on the campsite document so the average can be updated in the same
 * transaction as the rating itself, without re-reading every rating.
 *
 * @param total The sum of all stars.
 * @param count How many ratings there are.
 */
data class RatingTotals(
    val total: Long = 0,
    val count: Long = 0
) {
    /** The average rating, or 0.0 if there are no ratings. */
    val average: Double
        get() = if (count <= 0) 0.0 else total.toDouble() / count

    /**
     * The totals after one rating changes.
     *
     * @param oldStars The entrant's previous stars, or null if they hadn't rated yet.
     * @param newStars The entrant's new stars, or null if the rating is being removed.
     */
    fun replace(oldStars: Int?, newStars: Int?): RatingTotals {
        var newTotal = total
        var newCount = count
        if (oldStars != null) {
            newTotal -= oldStars
            newCount -= 1
        }
        if (newStars != null) {
            newTotal += newStars
            newCount += 1
        }
        return RatingTotals(maxOf(newTotal, 0), maxOf(newCount, 0))
    }
}