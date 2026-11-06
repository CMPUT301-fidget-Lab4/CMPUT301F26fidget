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

    /**
     * Adds an entrant to the waitlist.
     *
     * Entrants can only join while registration is open.
     * This only updates the lottery locally.
     *
     * @param user The entrant joining.
     * @param now The current date and time.
     * @return true if the entrant was added.
     */
    fun joinWaitlist(
        user: User,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {

        // Only entrants or admins can join
        if (user.userType != "entrant" && user.userType != "admin") {
            return false
        }

        // Lottery and entrant must exist
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

        // Cannot join after the draw
        if (drawn) {
            return false
        }

        // No duplicate entries
        if (waitlist.contains(user.deviceID) || winners.contains(user.deviceID)) {
            return false
        }

        // Waitlist capacity (0 means unlimited)
        if (waitlistCapacity > 0 && waitlist.size >= waitlistCapacity) {
            return false
        }

        waitlist = waitlist + user.deviceID
        return true
    }

    /**
     * Randomly picks winners from the waitlist.
     *
     * Only allowed once, and only after registration closes.
     *
     * @param now The current date and time.
     * @param random Random generator, can be fixed in tests.
     * @return true if winners were drawn.
     */
    fun drawWinners(
        now: LocalDateTime = LocalDateTime.now(),
        random: Random = Random.Default
    ): Boolean {

        if (id.isBlank()) {
            return false
        }

        // Only draw once
        if (drawn) {
            return false
        }

        // Wait until registration closes
        if (now.isBefore(period.registrationEnd)) {
            return false
        }

        val entrants = waitlist
            .filter { it.isNotBlank() }
            .distinct()

        if (entrants.isEmpty()) {
            return false
        }

        winners = entrants
            .shuffled(random)
            .take(numWinners)

        drawn = true
        return true
    }

    /**
     * Checks whether a user won this lottery.
     *
     * @param user The user to check.
     * @return true if the lottery was drawn and the user won.
     */
    fun hasWon(user: User): Boolean {
        return drawn &&
                user.deviceID.isNotBlank() &&
                winners.contains(user.deviceID)
    }

    /**
     * Makes one INVITED reservation for each winner.
     * Call this right after drawWinners() and save the results.
     *
     * @return The invitations, or an empty list if not drawn yet.
     */
    fun createInvitations(): List<Reservation> {
        if (!drawn) {
            return emptyList()
        }

        return winners.map { winnerId ->
            Reservation(
                lotteryId = id,
                ownerId = ownerId,
                entrantId = winnerId
            )
        }
    }
}