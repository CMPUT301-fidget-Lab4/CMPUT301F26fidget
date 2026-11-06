package com.example.myapplication.features

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * All the bookings that came out of one lottery.
 *
 * The owner sets a booking period (bookingStart to bookingEnd).
 * Winners pick dates inside that period, and each pick becomes a Booking.
 * A campsite can have several of these, one per lottery.
 *
 * @param registration How people got the right to book (a Lottery).
 * @param bookingStart Start of the period winners can book in.
 * @param bookingEnd End of the period winners can book in.
 */
class BookingSchedule<RegistrationType : Any>(
    val registration: RegistrationType,
    var bookingStart: LocalDateTime = LocalDateTime.now(),
    var bookingEnd: LocalDateTime = LocalDateTime.now()
) {
    private val bookings = mutableListOf<Booking>()

    /**
     * The dates one entrant booked one campsite for.
     *
     * @param entrantId Device ID of the entrant.
     * @param campsiteId The campsite they booked.
     * @param startDate When their stay starts.
     * @param endDate When their stay ends.
     */
    inner class Booking(
        val entrantId: String,
        val campsiteId: String,
        val startDate: LocalDateTime,
        val endDate: LocalDateTime
    ) {
        init {
            require(endDate.isAfter(startDate)) { "End date must be after start date." }
        }

        /** true if this booking covers any part of the given day. */
        fun covers(day: LocalDate): Boolean {
            val dayStart = day.atStartOfDay()
            val dayEnd = day.plusDays(1).atStartOfDay()
            return startDate.isBefore(dayEnd) && endDate.isAfter(dayStart)
        }
    }

    /**
     * Adds a booking if it fits.
     *
     * The dates must be inside the booking period, the entrant can only
     * have one booking in this schedule, and the campsite must have room
     * on every day of the stay.
     *
     * @param booking The booking to add.
     * @param occupancy Maximum number of people the campsite holds.
     * @return true if the booking was added.
     */
    fun addBooking(booking: Booking, occupancy: Int): Boolean {

        // Must be inside the booking period
        if (booking.startDate.isBefore(bookingStart) ||
            booking.endDate.isAfter(bookingEnd)
        ) {
            return false
        }

        // One booking per entrant
        if (bookings.any { it.entrantId == booking.entrantId }) {
            return false
        }

        // Campsite must have room on every day
        if (!hasRoom(booking.campsiteId, booking.startDate, booking.endDate, occupancy)) {
            return false
        }

        return bookings.add(booking)
    }

    /**
     * Puts back a booking that was already saved in Firestore.
     *
     * Skips the checks in addBooking(), because the booking was checked
     * when it was first made. Only use this when loading.
     */
    fun restoreBooking(booking: Booking) {
        bookings.add(booking)
    }

    /**
     * Removes an entrant's booking, freeing up their dates.
     *
     * @param entrantId Device ID of the entrant.
     * @return true if a booking was removed.
     */
    fun removeBooking(entrantId: String): Boolean {
        return bookings.removeAll { it.entrantId == entrantId }
    }

    /** All bookings, earliest first. */
    fun getBookings(): List<Booking> = bookings.sortedBy { it.startDate }

    /**
     * Bookings happening at a given moment.
     *
     * @param time The moment to check.
     * @param campsiteId Only count this campsite, or null for all.
     */
    fun getBookingsAt(
        time: LocalDateTime = LocalDateTime.now(),
        campsiteId: String? = null
    ): List<Booking> =
        getBookings().filter { booking ->
            (campsiteId == null || booking.campsiteId == campsiteId) &&
                    !time.isBefore(booking.startDate) &&
                    time.isBefore(booking.endDate)
        }

    /**
     * Bookings for one campsite that cover any part of a day.
     */
    fun bookingsOn(campsiteId: String, day: LocalDate): List<Booking> =
        bookings.filter { it.campsiteId == campsiteId && it.covers(day) }

    /**
     * Checks whether a campsite has room for one more person
     * on every day between start and end.
     *
     * @param occupancy Maximum number of people the campsite holds.
     */
    fun hasRoom(
        campsiteId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        occupancy: Int
    ): Boolean {
        var day = start.toLocalDate()
        while (day.atStartOfDay().isBefore(end)) {
            if (bookingsOn(campsiteId, day).size >= occupancy) {
                return false
            }
            day = day.plusDays(1)
        }
        return true
    }

    /**
     * Days in the booking period where the campsite still has room.
     * Used to show winners which dates they can pick.
     *
     * @param occupancy Maximum number of people the campsite holds.
     */
    fun availableDates(campsiteId: String, occupancy: Int): List<LocalDate> =
        daysInPeriod().filter { bookingsOn(campsiteId, it).size < occupancy }

    /**
     * Days in the booking period where the campsite has at least one booking.
     * Used to show the owner their booked dates.
     */
    fun bookedDates(campsiteId: String): List<LocalDate> =
        daysInPeriod().filter { bookingsOn(campsiteId, it).isNotEmpty() }

    private fun daysInPeriod(): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var day = bookingStart.toLocalDate()
        while (day.atStartOfDay().isBefore(bookingEnd)) {
            days.add(day)
            day = day.plusDays(1)
        }
        return days
    }
}