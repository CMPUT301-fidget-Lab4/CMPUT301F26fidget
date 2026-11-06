package com.example.myapplication.features

import com.google.firebase.firestore.Exclude
import java.time.LocalDateTime

/**
 * Where a reservation is in its life.
 *
 * INVITED   - entrant won the lottery and hasn't answered yet
 * RESERVED  - entrant picked a campsite and dates
 * DECLINED  - entrant said no to the invitation
 * CANCELLED - entrant cancelled after reserving
 * REJECTED  - owner rejected the reservation
 */
enum class ReservationStatus { INVITED, RESERVED, DECLINED, CANCELLED, REJECTED }

/**
 * The contract between one entrant and one lottery.
 *
 * Created as INVITED when the entrant wins. If they reserve, the campsite
 * and dates are copied here (as well as into a Booking) so Firestore can
 * filter reservations by campsite, status and date.
 *
 * Every field has a default so Firestore's toObject() works. Dates are
 * stored as LocalDateTime.toString() because Firestore can't store
 * LocalDateTime directly.
 *
 * @param id Firestore document id.
 * @param lotteryId The lottery this came from.
 * @param ownerId Device ID of the campsite owner.
 * @param entrantId Device ID of the entrant.
 * @param campsiteId The campsite reserved, blank until reserved.
 * @param bookingStart Start of the stay, blank until reserved.
 * @param bookingEnd End of the stay, blank until reserved.
 * @param status Current status.
 * @param createdAt When the invitation was made, in milliseconds.
 * @param updatedAt When the status last changed, in milliseconds.
 */
data class Reservation(
    var id: String = "",
    var lotteryId: String = "",
    var ownerId: String = "",
    var entrantId: String = "",
    var campsiteId: String = "",
    var bookingStart: String = "",
    var bookingEnd: String = "",
    var status: ReservationStatus = ReservationStatus.INVITED,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = createdAt
) {

    /** Start of the stay as a date, or null if not reserved yet. */
    @get:Exclude
    val bookingStartDate: LocalDateTime?
        get() = if (bookingStart.isBlank()) null else LocalDateTime.parse(bookingStart)

    /** End of the stay as a date, or null if not reserved yet. */
    @get:Exclude
    val bookingEndDate: LocalDateTime?
        get() = if (bookingEnd.isBlank()) null else LocalDateTime.parse(bookingEnd)

    /**
     * Active means waiting for an answer, or reserved for a stay
     * that hasn't ended yet.
     */
    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean {
        return when (status) {
            ReservationStatus.INVITED -> true
            ReservationStatus.RESERVED -> bookingEndDate?.isAfter(now) ?: true
            else -> false
        }
    }

    /** Past means reserved for a stay that has already ended. */
    fun isPast(now: LocalDateTime = LocalDateTime.now()): Boolean {
        return status == ReservationStatus.RESERVED &&
                bookingEndDate?.isAfter(now) == false
    }


    // ---------------------------------------------------------------
    // Entrant actions
    // ---------------------------------------------------------------

    /**
     * Accepts the invitation by picking a campsite and dates.
     *
     * Adds a Booking to the schedule, which checks the dates are in the
     * booking period and the campsite has room.
     *
     * @param schedule The booking schedule for this reservation's lottery.
     * @param campsiteId The campsite the entrant picked.
     * @param start Start of their stay.
     * @param end End of their stay.
     * @param occupancy Maximum number of people the campsite holds.
     * @return true if the reservation was made.
     */
    fun reserve(
        schedule: BookingSchedule<Lottery>,
        campsiteId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        occupancy: Int
    ): Boolean {

        // Must still be waiting for an answer
        if (status != ReservationStatus.INVITED) {
            return false
        }

        // Schedule must belong to this reservation's lottery
        val lottery = schedule.registration
        if (lottery.id != lotteryId) {
            return false
        }

        // Campsite must be in the lottery
        if (campsiteId !in lottery.campsiteIds) {
            return false
        }

        // End must be after start (checked here so Booking's require() never throws)
        if (!end.isAfter(start)) {
            return false
        }

        // Adds the booking only if the dates fit and there's room
        val booking = schedule.Booking(entrantId, campsiteId, start, end)
        if (!schedule.addBooking(booking, occupancy)) {
            return false
        }

        this.campsiteId = campsiteId
        bookingStart = start.toString()
        bookingEnd = end.toString()
        moveTo(ReservationStatus.RESERVED)
        return true
    }

    /**
     * Says no to the invitation.
     *
     * @return true if declined.
     */
    fun decline(): Boolean {
        if (status != ReservationStatus.INVITED) {
            return false
        }
        moveTo(ReservationStatus.DECLINED)
        return true
    }

    /**
     * Cancels a reservation before the stay starts.
     * Frees up the dates in the schedule.
     *
     * @param schedule The booking schedule for this reservation's lottery.
     * @param now The current date and time.
     * @return true if cancelled.
     */
    fun cancel(
        schedule: BookingSchedule<Lottery>,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (status != ReservationStatus.RESERVED) {
            return false
        }

        // Can't cancel a stay that has already started
        val start = bookingStartDate ?: return false
        if (!now.isBefore(start)) {
            return false
        }

        schedule.removeBooking(entrantId)
        moveTo(ReservationStatus.CANCELLED)
        return true
    }


    // ---------------------------------------------------------------
    // Owner actions
    // ---------------------------------------------------------------

    /**
     * Owner rejects an invitation or a reservation.
     * If it was reserved, the dates are freed up.
     *
     * @param schedule The booking schedule for this reservation's lottery.
     * @return true if rejected.
     */
    fun reject(schedule: BookingSchedule<Lottery>): Boolean {
        if (status != ReservationStatus.INVITED &&
            status != ReservationStatus.RESERVED
        ) {
            return false
        }

        if (status == ReservationStatus.RESERVED) {
            schedule.removeBooking(entrantId)
        }

        moveTo(ReservationStatus.REJECTED)
        return true
    }


    private fun moveTo(next: ReservationStatus) {
        status = next
        updatedAt = System.currentTimeMillis()
    }
}