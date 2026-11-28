package com.example.myapplication.features.lottery

import com.example.myapplication.core.datetime.AlbertaTime

import java.time.LocalDateTime

class Registration (
    var registrationStart: LocalDateTime = LocalDateTime.now(AlbertaTime.zone),
    var registrationEnd: LocalDateTime = LocalDateTime.now(AlbertaTime.zone),
)
{
    /**
     * Registration is open from the start time
     * until, but not including, the end time.
     */
    fun registrationIsOpen(
        now: LocalDateTime = LocalDateTime.now(AlbertaTime.zone)
    ): Boolean {
        return !now.isBefore(registrationStart) &&
                now.isBefore(registrationEnd)
    }
}