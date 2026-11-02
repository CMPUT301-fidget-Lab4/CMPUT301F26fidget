package com.example.myapplication.features

import java.time.LocalDate
import java.time.LocalDateTime

data class Reservation(
    var registrationStart: LocalDateTime = LocalDateTime.now(),
    var registrationEnd: LocalDateTime = LocalDateTime.now(),
    var bookingStart: LocalDateTime = LocalDateTime.now(),
    var bookingEnd: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Registration is open from the start time
     * until, but not including, the end time.
     */
    fun registrationIsOpen(
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        return !now.isBefore(registrationStart) &&
                now.isBefore(registrationEnd)
    }

    /**
     * Checks whether the booking period is open.
     */
    fun bookingIsOpen(
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        return !now.isBefore(bookingStart) &&
                now.isBefore(bookingEnd)
    }
}

