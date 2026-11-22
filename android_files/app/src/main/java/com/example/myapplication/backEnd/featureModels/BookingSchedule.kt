package com.example.myapplication.backEnd.featureModels

import com.example.myapplication.backEnd.time.AlbertaTime

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * All the bookings that came out of one lottery.
 *
 * The owner sets a default booking period (bookingStart to bookingEnd),
 * with optional overrides for individual campsites in the same lottery.
 * Stay windows are unassigned Bookings; a winner's selected stay is a Booking with an entrant.
 * A campsite can have several of these, one per lottery.
 *
 * @param registration How people got the right to book (a Lottery).
 * @param bookingStart Start of the period winners can book in.
 * @param bookingEnd End of the period winners can book in.
 * @param campsitePeriods Unassigned Bookings defining each campsite's stay window; absent entries use the default.
 */
class BookingSchedule<RegistrationType : Any>(
    val registration: RegistrationType,
    var bookingStart: LocalDateTime = LocalDateTime.now(AlbertaTime.zone),
    var bookingEnd: LocalDateTime = LocalDateTime.now(AlbertaTime.zone),
    campsitePeriods: Map<String, Booking> = emptyMap()
) {
    val campsitePeriods: Map<String, Booking> = campsitePeriods.toMap()
    private val bookings = mutableListOf<Booking>()

    init {
        require(campsitePeriods.all { (id, booking) ->
            id.isNotBlank() && booking.campsiteId == id && booking.entrantId == null
        }) { "Campsite stay windows must be unassigned bookings for their campsite." }
    }

    /** An unassigned Booking describing the dates allowed at this campsite. */
    fun periodFor(campsiteId: String): Booking =
        campsitePeriods[campsiteId] ?: Booking(campsiteId = campsiteId, startDate = bookingStart, endDate = bookingEnd)

    /**
     * A campsite's stay window or the dates one entrant booked it for.
     * This nested class can be built before its schedule exists.
     *
     * @param entrantId Device ID for a confirmed stay; null for an available stay window.
     * @param campsiteId The campsite, or empty for a shared window being validated.
     * @param startDate When their stay starts.
     * @param endDate When their stay ends.
     */
    data class Booking(
        val entrantId: String? = null,
        val campsiteId: String = "",
        val startDate: LocalDateTime,
        val endDate: LocalDateTime
    ) {
        init {
            require(endDate.isAfter(startDate)) { "End date must be after start date." }
        }

        fun validationError(registrationEnd: LocalDateTime): String? =
            validationError(startDate, endDate, registrationEnd)

        fun contains(start: LocalDateTime, end: LocalDateTime): Boolean =
            end.isAfter(start) && !start.isBefore(startDate) && !end.isAfter(endDate)

        fun overlaps(other: Booking): Boolean =
            startDate.isBefore(other.endDate) && endDate.isAfter(other.startDate)

        companion object {
            /** Checks raw dates too, so invalid form values can be reported before constructing a Booking. */
            fun validationError(start: LocalDateTime, end: LocalDateTime, registrationEnd: LocalDateTime): String? {
                if (!end.isAfter(start)) return "Booking must end after it starts."
                if (start.isBefore(registrationEnd)) return "Booking cannot start before registration closes."
                val midnight = start.toLocalDate().atStartOfDay()
                val firstNight = if (midnight.isBefore(start)) midnight.plusDays(1) else midnight
                if (firstNight.plusDays(1).isAfter(end)) {
                    return "Choose a booking period that includes at least one full night, from midnight to midnight."
                }
                return null
            }
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

        // Available windows belong in campsitePeriods, never in the confirmed-booking list.
        if (booking.entrantId.isNullOrBlank() || booking.campsiteId.isBlank()) return false

        // Must be inside the booking period
        if (!periodFor(booking.campsiteId).contains(booking.startDate, booking.endDate)) {
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
        require(!booking.entrantId.isNullOrBlank() && booking.campsiteId.isNotBlank()) {
            "Only an entrant's confirmed booking can be restored."
        }
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
        time: LocalDateTime = LocalDateTime.now(AlbertaTime.zone),
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
        daysInPeriod(campsiteId).filter { bookingsOn(campsiteId, it).size < occupancy }

    /**
     * Days in the booking period where the campsite has at least one booking.
     * Used to show the owner their booked dates.
     */
    fun bookedDates(campsiteId: String): List<LocalDate> =
        daysInPeriod(campsiteId).filter { bookingsOn(campsiteId, it).isNotEmpty() }

    private fun daysInPeriod(campsiteId: String): List<LocalDate> {
        val period = periodFor(campsiteId)
        val days = mutableListOf<LocalDate>()
        var day = period.startDate.toLocalDate()
        if (day.atStartOfDay().isBefore(period.startDate)) day = day.plusDays(1)
        while (!day.plusDays(1).atStartOfDay().isAfter(period.endDate)) {
            days.add(day)
            day = day.plusDays(1)
        }
        return days
    }
}
