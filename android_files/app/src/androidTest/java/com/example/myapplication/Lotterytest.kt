package com.example.myapplication

import com.example.myapplication.features.Campsite
import com.example.myapplication.features.Lottery
import com.example.myapplication.features.Reservation
import com.example.myapplication.features.User
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class LotteryWinnerTest {

    @Test
    fun winnersCannotExceedOccupancy() {
        val campsite = Campsite(
            name = "TestCamp",
            occupancy = 2
        )

        assertFalse(
            campsite.createLottery(3, 0, emptyList())
        )

        assertTrue(
            campsite.createLottery(2, 0, emptyList())
        )
    }

    @Test
    fun winnersAreSelectedAfterRegistrationCloses() {

        val closing = LocalDateTime.of(2035, 6, 10, 17, 0)

        val lottery = Lottery(
            id = "test-lottery",
            numWinners = 2,
            period = Reservation(
                registrationStart = closing.minusDays(1),
                registrationEnd = closing,
                bookingStart = closing.plusDays(1),
                bookingEnd = closing.plusDays(2)
            ),
            waitlist = listOf(
                "user1", "user2", "user3"
            )
        )

        // Drawing before closing should fail.
        assertFalse(
            lottery.drawWinners(closing.minusMinutes(1))
        )

        // Drawing at closing should work.
        assertTrue(
            lottery.drawWinners(closing, Random(42))
        )

        assertEquals(2, lottery.winners.size)
        assertEquals(2, lottery.winners.distinct().size)
        assertTrue(lottery.drawn)

        // Lottery cannot be redrawn.
        assertFalse(lottery.drawWinners(closing))
    }

    @Test
    fun userCanSeeJoinedLotteries() {

        val user = User(
            userType = "entrant",
            deviceID = "user123"
        )

        assertTrue(
            user.addJoinedLottery("lottery1", "CampA")
        )

        assertTrue(
            user.addJoinedLottery("lottery2", "CampB")
        )

        assertFalse(
            user.addJoinedLottery("lottery1", "CampA")
        )


    }
}
