package com.example.myapplication.features

data class Lottery(
    var id: String = "",
    var campsiteIds: List<String> = listOf(),   // one or more campsites
    var period: Reservation = Reservation(),    // registration + booking dates
    var numWinners: Int = 1,
    var waitlistCapacity: Int = 0,              // 0 means no limit
    var waitlist: List<String> = listOf(),      // entrant ids
    var winners: List<String> = listOf(),
    var drawn: Boolean = false
)