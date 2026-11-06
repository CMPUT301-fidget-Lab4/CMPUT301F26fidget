package com.example.myapplication.features

import java.time.LocalDateTime

class Registration (
    var registrationStart: LocalDateTime = LocalDateTime.now(),
    var registrationEnd: LocalDateTime = LocalDateTime.now(),
)
{
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
}