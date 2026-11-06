package com.example.myapplication.features

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
data class Lottery(
    var id: String = "",
    var campsiteIds: List<String> = listOf(),   // one or more campsites
    var period: Registration = Registration(),    // registration
    var numWinners: Int = 1,
    var waitlistCapacity: Int = 0,              // 0 means no limit
    var waitlist: List<String> = listOf(),      // entrant ids
    var winners: List<String> = listOf(),
    var drawn: Boolean = false
) {


}