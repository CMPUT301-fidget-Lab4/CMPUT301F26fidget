package com.example.myapplication.features

import java.time.LocalDate

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
    fun joinWaitlist(user: User): Boolean {

        // Only entrants can join.
        if (!user.userType.equals("entrant", ignoreCase = true)) {
            return false
        }

        // The user and lottery must have IDs.
        if (user.deviceID.isBlank() || id.isBlank()) {
            return false
        }

        // Registration must be open today.
        val today = LocalDate.now()

        if (today.isBefore(period.registrationStart) ||
            today.isAfter(period.registrationEnd)
        ) {
            return false
        }

        // Cannot join after winners have been drawn.
        if (drawn) {
            return false
        }

        // Prevent duplicate entries.
        if (waitlist.contains(user.deviceID) ||
            winners.contains(user.deviceID)
        ) {
            return false
        }

        // Check capacity (0 = unlimited).
        if (waitlistCapacity > 0 &&
            waitlist.size >= waitlistCapacity
        ) {
            return false
        }

        // Add the entrant locally.
        waitlist = waitlist + user.deviceID

        return true
    }
}