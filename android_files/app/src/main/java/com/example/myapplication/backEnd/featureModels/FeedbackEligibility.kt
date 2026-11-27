package com.example.myapplication.backEnd.featureModels

import com.example.myapplication.backEnd.time.AlbertaTime
import java.time.LocalDateTime

/**
 * Decides who can rate or review a campsite.
 *
 * An entrant can give feedback once they have actually stayed there: they
 * need a RESERVED reservation at that campsite whose stay has started.
 * This covers "check the entrant actually won the lottery to stay at the
 * campsite" from US 01.13.01, since only winners get reservations.
 */
object FeedbackEligibility {

    /**
     * Checks whether an entrant's stay at a campsite has started.
     *
     * @param reservations The entrant's reservations (others are ignored).
     * @param entrantId The entrant's device ID.
     * @param campsiteId The campsite they want to rate or review.
     * @param now The current date and time.
     * @return true if they have a reserved stay there that has started.
     */
    fun hasStayStarted(
        reservations: List<Reservation>,
        entrantId: String,
        campsiteId: String,
        now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
    ): Boolean {
        if (entrantId.isBlank() || campsiteId.isBlank()) {
            return false
        }

        return reservations.any { reservation ->
            val start = runCatching { reservation.bookingStartDate }.getOrNull()
            reservation.entrantId == entrantId &&
                    reservation.campsiteId == campsiteId &&
                    reservation.status == ReservationStatus.RESERVED &&
                    start != null &&
                    !now.isBefore(start)
        }
    }
}