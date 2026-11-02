package com.example.myapplication.features

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
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

        // Should update our firebase
        return true
    }


    /**
     * Randomly selects lottery winners from the waitlist.
     *
     * The draw is allowed only after registration closes.
     * Each entrant can win only once, and the lottery
     * cannot be drawn more than once.
     *
     * @param now Current date and time.
     * @param random Random generator used for selecting winners.
     * @return true if winners were successfully drawn.
     */
    fun drawWinners(
        now: LocalDateTime = LocalDateTime.now(),
        random: Random = Random.Default
    ): Boolean {

        // Check that the lottery exists.
        if (id.isBlank()) {
            return false
        }

        // Do not draw more than once.
        if (drawn) {
            return false
        }

        // Wait until registration closes.
        if (now.isBefore(period.registrationEnd)) {
            return false
        }

        // No entrants means no winners.
        val entrants = waitlist
            .filter { it.isNotBlank() }
            .distinct()

        if (entrants.isEmpty()) {
            return false
        }

        // Randomly select up to numWinners unique entrants.
        winners = entrants
            .shuffled(random)
            .take(numWinners)

        //TODO(" Then attach notificaiton function later in the future")
        //TODo("Figure a way for the firebase to automatically draw a user")
        drawn = true

        return true
    }

    /**
     * Checks whether an entrant won this lottery.
     *
     * @param user User whose result is being checked.
     * @return true if the lottery was drawn and the user won.
     */
    fun hasWon(user: User): Boolean {
        return drawn &&
                user.deviceID.isNotBlank() &&
                winners.contains(user.deviceID)
    }


}