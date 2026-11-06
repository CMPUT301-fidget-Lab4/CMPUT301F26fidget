package com.example.myapplication.features

import java.time.LocalDateTime

class BookingSchedule<RegistrationType : Any>(
    val registration: RegistrationType
) {
    private val bookings = mutableListOf<Booking>()

    inner class Booking(
        val startDate: LocalDateTime = LocalDateTime.now(),
        val endDate: LocalDateTime
    ) {
        init {
            require(endDate.isAfter(startDate)) { "End date must be after start date." }
        }
    }

    fun addBooking(booking: Booking): Boolean {
        if (booking in bookings) return false
        return bookings.add(booking)
    }

    fun getBookings(): List<Booking> = bookings.sortedBy { it.startDate }

    fun getBookingsAt(time: LocalDateTime = LocalDateTime.now()): List<Booking> =
        getBookings().filter { booking ->
            !time.isBefore(booking.startDate) && time.isBefore(booking.endDate)
        }
}
