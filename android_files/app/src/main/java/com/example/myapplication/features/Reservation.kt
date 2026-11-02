package com.example.myapplication.features

import java.time.LocalDate

data class Reservation(
    var registrationStart: LocalDate = LocalDate.now(),  // entrants can register from this day
    var registrationEnd: LocalDate = LocalDate.now(),    // entrants can't register/book after this
    var bookingStart: LocalDate = LocalDate.now(),       // first day winners may book
    var bookingEnd: LocalDate = LocalDate.now()          // last day winners may book
) {

    fun hasValidDates(): Boolean {
        return !registrationEnd.isBefore(registrationStart) &&
                !bookingStart.isBefore(registrationEnd) &&
                !bookingEnd.isBefore(bookingStart)
    }

    fun registrationIsOpen(now: LocalDate = LocalDate.now()): Boolean {
        return !now.isBefore(registrationStart) && now.isBefore(registrationEnd)
    }

    fun bookingIsOpen(now: LocalDate = LocalDate.now()): Boolean {
        return !now.isBefore(bookingStart) && !now.isAfter(bookingEnd)
    }



}