package com.example.myapplication.features

import java.time.LocalDate
import java.time.LocalDateTime

data class Lottery(
    var id: String = "",
    var campsiteIds: List<String> = listOf(),   // one or more campsites
    var period: Reservation = Reservation(),    // registration + booking dates
    var numWinners: Int = 1,
    var waitlistCapacity: Int = 0,              // 0 means no limit
    var waitlist: List<String> = listOf(),      // entrant ids
    var winners: List<String> = listOf(),
    var drawn: Boolean = false
) {

    /**
     * Adds an entrant to the lottery waitlist.
     *
     * Entrants can only join while registration is open.
     * Capacity 0 means the waitlist is unlimited.
     *
     * This function only updates the Lottery object locally.
     *
     * @param user The user joining the lottery.
     * @return true if the entrant was added, false otherwise.
     */

    /**
     * Adds an entrant to the lottery waitlist.
     *
     * Entrants can only join while registration is open.
     * This function only updates the lottery locally.
     *
     * @param user The entrant joining the waitlist.
     * @param now The current date and time.
     * @return true if the entrant was added, false otherwise.
     */
    fun joinWaitlist(
        user: User,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {

        // Only entrants can join
        if (user.userType != "entrant") {
            return false
        }

        // Check that the lottery and entrant exist
        if (id.isBlank() || user.deviceID.isBlank()) {
            return false
        }

        // Registration end must be after registration start
        if (!period.registrationEnd.isAfter(period.registrationStart)) {
            return false
        }

        // Registration must be open
        if (!period.registrationIsOpen(now)) {
            return false
        }

        // Cannot join after the lottery has been drawn
        if (drawn) {
            return false
        }

        // Prevent duplicate entries
        if (waitlist.contains(user.deviceID) ||
            winners.contains(user.deviceID)
        ) {
            return false
        }

        // Check waitlist capacity (0 means unlimited)
        if (waitlistCapacity > 0 &&
            waitlist.size >= waitlistCapacity
        ) {
            return false
        }

        // Add entrant to the waitlist
        waitlist = waitlist + user.deviceID

        return true
    }

}