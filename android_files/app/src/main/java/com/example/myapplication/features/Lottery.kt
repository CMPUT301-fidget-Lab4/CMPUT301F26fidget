package com.example.myapplication.features

import java.time.LocalDateTime
import kotlin.random.Random

/**
 * A lottery for one or more campsites.
 *
 * Entrants join the waitlist while registration is open. After it
 * closes, winners are drawn and each one gets an INVITED Reservation.
 * The bookings that come out of it live in a BookingSchedule<Lottery>.
 *
 * @param id Unique id of the lottery.
 * @param ownerId Device ID of the campsite owner.
 * @param campsiteIds The campsites in this lottery.
 * @param period The registration period.
 * @param numWinners How many entrants win.
 * @param waitlistCapacity Maximum entrants, 0 means no limit.
 * @param waitlist Device IDs of entrants who joined.
 * @param winners Device IDs of entrants who won.
 * @param drawn true once winners have been drawn.
 */
data class Lottery(
    var id: String = "",
    var ownerId: String = "",
    var campsiteIds: List<String> = listOf(),
    var period: Registration = Registration(),
    var numWinners: Int = 1,
    var waitlistCapacity: Int = 0,
    var waitlist: List<String> = listOf(),
    var winners: List<String> = listOf(),
    var drawn: Boolean = false
) {

}