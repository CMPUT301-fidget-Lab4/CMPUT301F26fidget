package com.example.myapplication

import com.example.myapplication.backEnd.featureModels.FeedbackEligibility
import com.example.myapplication.backEnd.featureModels.Rating
import com.example.myapplication.backEnd.featureModels.RatingTotals
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import com.example.myapplication.backEnd.featureModels.Review
import com.example.myapplication.backEnd.userModels.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Tests for ratings, reviews and who may give them
 * (US 01.13.01, US 01.13.02, US 03.09.01, US 03.10.01).
 */
class FeedbackTest {

    /** If User becomes abstract, change this to build the matching subclass. */
    private fun user(id: String) = User(userType = "entrant", deviceID = id)

    private val entrant get() = user("entrant1")

    private val checkIn = LocalDateTime.of(2035, 7, 1, 0, 0)

    private fun reservation(
        entrantId: String = "entrant1",
        campsiteId: String = "CampA",
        status: ReservationStatus = ReservationStatus.RESERVED,
        bookingStart: String = "2035-07-01T00:00",
        bookingEnd: String = "2035-07-03T00:00"
    ) = Reservation(
        id = "res1",
        lotteryId = "lottery1",
        ownerId = "owner1",
        entrantId = entrantId,
        campsiteId = campsiteId,
        bookingStart = bookingStart,
        bookingEnd = bookingEnd,
        status = status
    )


    // ---------------------------------------------------------------
    // Rating
    // ---------------------------------------------------------------

    @Test
    fun ratingFromOneToFiveIsAllowed() {
        for (stars in 1..5) {
            assertNotNull("$stars stars should be allowed", Rating.create("CampA", entrant, stars))
        }
    }

    @Test
    fun ratingOutsideOneToFiveIsRefused() {
        assertNull(Rating.create("CampA", entrant, 0))
        assertNull(Rating.create("CampA", entrant, 6))
        assertNull(Rating.create("CampA", entrant, -1))
    }

    @Test
    fun ratingNeedsCampsiteAndEntrant() {
        assertNull(Rating.create("", entrant, 4))
        assertNull(Rating.create("CampA", user(""), 4))
    }

    @Test
    fun ratingHasOneIdPerEntrantPerCampsite() {
        val rating = Rating.create("CampA", entrant, 4, now = 100L)!!

        assertEquals("CampA_entrant1", rating.id)
        assertEquals(Rating.idFor("CampA", "entrant1"), rating.id)
        assertEquals("CampA", rating.campsiteId)
        assertEquals("entrant1", rating.entrantId)
        assertEquals(4, rating.stars)
        assertEquals(100L, rating.createdAt)
    }


    // ---------------------------------------------------------------
    // Average rating
    // ---------------------------------------------------------------

    @Test
    fun noRatingsAverageZero() {
        assertEquals(0.0, RatingTotals().average, 0.0001)
    }

    @Test
    fun addingRatingsUpdatesAverage() {
        val totals = RatingTotals()
            .replace(null, 5)
            .replace(null, 3)

        assertEquals(8L, totals.total)
        assertEquals(2L, totals.count)
        assertEquals(4.0, totals.average, 0.0001)
    }

    @Test
    fun changingARatingKeepsCount() {
        val totals = RatingTotals(total = 8, count = 2)
            .replace(oldStars = 3, newStars = 1)

        assertEquals(6L, totals.total)
        assertEquals(2L, totals.count)
        assertEquals(3.0, totals.average, 0.0001)
    }

    @Test
    fun removingARatingDropsItFromAverage() {
        val totals = RatingTotals(total = 8, count = 2)
            .replace(oldStars = 3, newStars = null)

        assertEquals(5L, totals.total)
        assertEquals(1L, totals.count)
        assertEquals(5.0, totals.average, 0.0001)
    }

    @Test
    fun removingLastRatingResetsToZero() {
        val totals = RatingTotals(total = 4, count = 1).replace(4, null)

        assertEquals(0L, totals.count)
        assertEquals(0.0, totals.average, 0.0001)
    }

    @Test
    fun totalsNeverGoNegative() {
        val totals = RatingTotals().replace(oldStars = 5, newStars = null)

        assertEquals(0L, totals.total)
        assertEquals(0L, totals.count)
    }


    // ---------------------------------------------------------------
    // Review
    // ---------------------------------------------------------------

    @Test
    fun reviewTextIsTrimmed() {
        val review = Review.create("CampA", entrant, "   Great views!   ")!!

        assertEquals("Great views!", review.text)
        assertEquals("CampA_entrant1", review.id)
    }

    @Test
    fun blankReviewIsRefused() {
        assertNull(Review.create("CampA", entrant, ""))
        assertNull(Review.create("CampA", entrant, "    "))
    }

    @Test
    fun reviewLengthLimit() {
        assertNotNull(Review.create("CampA", entrant, "a".repeat(Review.MAX_LENGTH)))
        assertNull(Review.create("CampA", entrant, "a".repeat(Review.MAX_LENGTH + 1)))
    }

    @Test
    fun reviewNeedsCampsiteAndEntrant() {
        assertNull(Review.create("", entrant, "Nice"))
        assertNull(Review.create("CampA", user(""), "Nice"))
    }

    @Test
    fun newReviewIsNotEdited() {
        val review = Review.create("CampA", entrant, "Nice", now = 100L)!!

        assertEquals(100L, review.createdAt)
        assertEquals(100L, review.updatedAt)
        assertFalse(review.edited)
    }

    @Test
    fun editingReviewKeepsOriginalPostingTime() {
        val review = Review.create("CampA", entrant, "Even nicer", now = 500L, createdAt = 100L)!!

        assertEquals(100L, review.createdAt)
        assertEquals(500L, review.updatedAt)
        assertTrue(review.edited)
    }


    // ---------------------------------------------------------------
    // Who can rate and review
    // ---------------------------------------------------------------

    @Test
    fun canGiveFeedbackOnceStayStarts() {
        val reservations = listOf(reservation())

        assertTrue(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn))
        assertTrue(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn.plusDays(5)))
    }

    @Test
    fun cannotGiveFeedbackBeforeStay() {
        val reservations = listOf(reservation())

        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn.minusMinutes(1)))
    }

    @Test
    fun cannotGiveFeedbackForAnotherCampsite() {
        val reservations = listOf(reservation(campsiteId = "CampB"))

        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn.plusDays(1)))
    }

    @Test
    fun cannotUseSomeoneElsesReservation() {
        val reservations = listOf(reservation(entrantId = "entrant2"))

        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn.plusDays(1)))
    }

    @Test
    fun onlyReservedStaysCount() {
        for (status in listOf(
            ReservationStatus.INVITED,
            ReservationStatus.DECLINED,
            ReservationStatus.CANCELLED,
            ReservationStatus.REJECTED
        )) {
            val reservations = listOf(reservation(status = status))
            assertFalse(
                "A $status reservation should not count",
                FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn.plusDays(1))
            )
        }
    }

    @Test
    fun noReservationsMeansNoFeedback() {
        assertFalse(FeedbackEligibility.hasStayStarted(emptyList(), "entrant1", "CampA", checkIn))
    }

    @Test
    fun blankIdsAreRefused() {
        val reservations = listOf(reservation())

        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "", "CampA", checkIn))
        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "", checkIn))
    }

    @Test
    fun badlyFormattedDatesAreIgnored() {
        val reservations = listOf(reservation(bookingStart = "not a date"))

        assertFalse(FeedbackEligibility.hasStayStarted(reservations, "entrant1", "CampA", checkIn))
    }
}