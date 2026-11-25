package com.example.myapplication.backEnd.featureModels

import com.example.myapplication.backEnd.userModels.User

/**
 * One entrant's written review of one campsite (US 01.13.02).
 *
 * Separate from [Rating]: an entrant can rate without reviewing, or review
 * without rating. Each entrant has at most one review per campsite; posting
 * again edits it. The document ID is "<campsiteId>_<entrantId>".
 *
 * Every field has a default so Firestore's toObject() works.
 *
 * @param id Firestore document id, see [idFor].
 * @param campsiteId The campsite being reviewed.
 * @param entrantId Device ID of the entrant who wrote it.
 * @param text The review.
 * @param createdAt When it was first posted, in milliseconds.
 * @param updatedAt When it was last edited, in milliseconds.
 */
data class Review(
    var id: String = "",
    var campsiteId: String = "",
    var entrantId: String = "",
    var text: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = createdAt
) {
    /** true if the review has been edited since it was posted. */
    val edited: Boolean
        @com.google.firebase.firestore.Exclude get() = updatedAt > createdAt

    companion object {
        /** Longest review allowed, in characters. */
        const val MAX_LENGTH = 1000

        /** The document ID for an entrant's review of a campsite. */
        fun idFor(campsiteId: String, entrantId: String): String = "${campsiteId}_$entrantId"

        /**
         * Builds a review after checking the text and IDs.
         * Whether the entrant has stayed at the campsite is checked separately,
         * see [FeedbackEligibility].
         *
         * @param createdAt Keep the original time when editing; null for a new review.
         * @return The review, or null if the input is invalid.
         */
        fun create(
            campsiteId: String,
            entrant: User,
            text: String,
            now: Long = System.currentTimeMillis(),
            createdAt: Long? = null
        ): Review? {
            val body = text.trim()
            if (campsiteId.isBlank() || entrant.deviceID.isBlank()) return null
            if (body.isEmpty() || body.length > MAX_LENGTH) return null

            return Review(
                id = idFor(campsiteId, entrant.deviceID),
                campsiteId = campsiteId,
                entrantId = entrant.deviceID,
                text = body,
                createdAt = createdAt ?: now,
                updatedAt = now
            )
        }
    }
}