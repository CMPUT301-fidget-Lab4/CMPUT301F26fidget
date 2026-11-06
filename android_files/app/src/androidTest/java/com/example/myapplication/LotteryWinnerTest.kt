package com.example.myapplication

import com.example.myapplication.features.Lottery
import com.example.myapplication.features.Registration
import com.example.myapplication.features.ReservationStatus
import com.example.myapplication.features.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class LotteryWinnerTest {

    private val opens = LocalDateTime.of(2035, 6, 9, 17, 0)
    private val closing = LocalDateTime.of(2035, 6, 10, 17, 0)

    private fun lottery(
        waitlist: List<String> = listOf(),
        numWinners: Int = 2,
        waitlistCapacity: Int = 0
    ) = Lottery(
        id = "test-lottery",
        ownerId = "owner1",
        campsiteIds = listOf("CampA"),
        period = Registration(
            registrationStart = opens,
            registrationEnd = closing
        ),
        numWinners = numWinners,
        waitlistCapacity = waitlistCapacity,
        waitlist = waitlist
    )

    private fun entrant(id: String) = User(userType = "entrant", deviceID = id)


    // ---------------------------------------------------------------
    // Joining the waitlist
    // ---------------------------------------------------------------

    @Test
    fun entrantCanJoinWhileRegistrationIsOpen() {
        val lottery = lottery()

        assertTrue(lottery.joinWaitlist(entrant("user1"), opens.plusHours(1)))
        assertEquals(listOf("user1"), lottery.waitlist)
    }

    @Test
    fun cannotJoinBeforeOrAfterRegistration() {
        val lottery = lottery()

        assertFalse(lottery.joinWaitlist(entrant("user1"), opens.minusMinutes(1)))
        assertFalse(lottery.joinWaitlist(entrant("user1"), closing))
        assertTrue(lottery.waitlist.isEmpty())
    }

    @Test
    fun cannotJoinTwice() {
        val lottery = lottery()
        val now = opens.plusHours(1)

        assertTrue(lottery.joinWaitlist(entrant("user1"), now))
        assertFalse(lottery.joinWaitlist(entrant("user1"), now))
        assertEquals(1, lottery.waitlist.size)
    }

    @Test
    fun waitlistCapacityIsEnforced() {
        val lottery = lottery(waitlistCapacity = 1)
        val now = opens.plusHours(1)

        assertTrue(lottery.joinWaitlist(entrant("user1"), now))
        assertFalse(lottery.joinWaitlist(entrant("user2"), now))
    }

    @Test
    fun campsiteOwnerCannotJoin() {
        val lottery = lottery()
        val owner = User(userType = "campsite owner", deviceID = "owner1")

        assertFalse(lottery.joinWaitlist(owner, opens.plusHours(1)))
    }


    // ---------------------------------------------------------------
    // Drawing winners
    // ---------------------------------------------------------------

    @Test
    fun winnersAreSelectedAfterRegistrationCloses() {
        val lottery = lottery(waitlist = listOf("user1", "user2", "user3"))

        // Drawing before closing should fail.
        assertFalse(lottery.drawWinners(closing.minusMinutes(1)))

        // Drawing at closing should work.
        assertTrue(lottery.drawWinners(closing, Random(42)))

        assertEquals(2, lottery.winners.size)
        assertEquals(2, lottery.winners.distinct().size)
        assertTrue(lottery.waitlist.containsAll(lottery.winners))
        assertTrue(lottery.drawn)

        // Lottery cannot be redrawn.
        assertFalse(lottery.drawWinners(closing))
    }

    @Test
    fun fewerEntrantsThanWinnersMeansEveryoneWins() {
        val lottery = lottery(waitlist = listOf("user1"), numWinners = 5)

        assertTrue(lottery.drawWinners(closing))
        assertEquals(listOf("user1"), lottery.winners)
    }

    @Test
    fun emptyWaitlistCannotBeDrawn() {
        assertFalse(lottery().drawWinners(closing))
    }

    @Test
    fun hasWonOnlyAfterDraw() {
        val lottery = lottery(waitlist = listOf("user1"), numWinners = 1)

        assertFalse(lottery.hasWon(entrant("user1")))

        lottery.drawWinners(closing)

        assertTrue(lottery.hasWon(entrant("user1")))
        assertFalse(lottery.hasWon(entrant("someoneElse")))
    }


    // ---------------------------------------------------------------
    // Invitations
    // ---------------------------------------------------------------

    @Test
    fun noInvitationsBeforeDraw() {
        val lottery = lottery(waitlist = listOf("user1", "user2"))

        assertTrue(lottery.createInvitations().isEmpty())
    }

    @Test
    fun oneInvitationPerWinner() {
        val lottery = lottery(waitlist = listOf("user1", "user2", "user3"))
        lottery.drawWinners(closing, Random(1))

        val invitations = lottery.createInvitations()

        assertEquals(lottery.winners.toSet(), invitations.map { it.entrantId }.toSet())
        invitations.forEach {
            assertEquals(ReservationStatus.INVITED, it.status)
            assertEquals("test-lottery", it.lotteryId)
            assertEquals("owner1", it.ownerId)
        }
    }


    // ---------------------------------------------------------------
    // User
    // ---------------------------------------------------------------

    @Test
    fun userCanSeeJoinedLotteries() {
        val user = User(
            userType = "entrant",
            deviceID = "user123"
        )

        assertTrue(user.addJoinedLottery("lottery1", "CampA"))
        assertTrue(user.addJoinedLottery("lottery2", "CampB"))
        assertFalse(user.addJoinedLottery("lottery1", "CampA"))
    }
}