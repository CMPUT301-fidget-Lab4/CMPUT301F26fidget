package com.example.myapplication.features

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/**
 * Waitlist opening/closing, optional capacity, winners, and campsite rules.
 * No Firebase required.
 * These tests check MODEL behavior; checkbox error messages and owner
 * permissions must also be tested at UI/backend level.
 */
class LotteryRulesTest {

    private val opens = LocalDateTime.of(2035, 6, 10, 9, 0)
    private val closes = LocalDateTime.of(2035, 6, 10, 17, 0)

    private fun entrant(id: String) = User(
        userType = "entrant",
        deviceID = id
    )

    private fun lottery(capacity: Int = 0) = Lottery(
        id = "lottery-test",
        campsiteIds = listOf("TestCampsite"),
        period = Reservation(
            registrationStart = opens,
            registrationEnd = closes,
            bookingStart = closes.plusDays(1),
            bookingEnd = closes.plusDays(3)
        ),
        numWinners = 2,
        waitlistCapacity = capacity
    )

    // ----- Registration window -----

    @Test
    fun rejectsEntrantBeforeRegistrationOpens() {
        assertFalse(lottery().joinWaitlist(entrant("A"), opens.minusNanos(1)))
    }

    @Test
    fun acceptsEntrantExactlyWhenRegistrationOpens() {
        assertTrue(lottery().joinWaitlist(entrant("A"), opens))
    }

    @Test
    fun acceptsEntrantWhileRegistrationIsOpen() {
        val lottery = lottery()
        assertTrue(lottery.joinWaitlist(entrant("A"), opens.plusHours(3)))
        assertEquals(listOf("A"), lottery.waitlist)
    }

    @Test
    fun rejectsEntrantExactlyAtClosingTime() {
        assertFalse(lottery().joinWaitlist(entrant("A"), closes))
    }

    @Test
    fun rejectsEntrantAfterClosingTime() {
        assertFalse(lottery().joinWaitlist(entrant("A"), closes.plusNanos(1)))
    }

    @Test
    fun rejectsDuplicateEntrantAndNonEntrant() {
        val lottery = lottery()
        assertTrue(lottery.joinWaitlist(entrant("A"), opens))
        assertFalse(lottery.joinWaitlist(entrant("A"), opens.plusMinutes(1)))
        assertFalse(
            lottery.joinWaitlist(
                User(userType = "owner", deviceID = "owner-1"),
                opens.plusMinutes(1)
            )
        )
        assertEquals(1, lottery.waitlist.size)
    }

    @Test
    fun drawnLotteryRejectsNewEntrants() {
        val lottery = lottery().apply { drawn = true }
        assertFalse(lottery.joinWaitlist(entrant("A"), opens))
    }

    // ----- Optional waitlist capacity -----

    @Test
    fun defaultZeroCapacityIsUnlimited() {
        val lottery = lottery() // 0 means unlimited
        repeat(10) { i ->
            assertTrue(lottery.joinWaitlist(entrant("entrant-$i"), opens))
        }
        assertEquals(10, lottery.waitlist.size)
    }

    @Test
    fun rejectsEntrantsOnceCapacityIsReached() {
        val lottery = lottery(capacity = 2)
        assertTrue(lottery.joinWaitlist(entrant("A"), opens))
        assertTrue(lottery.joinWaitlist(entrant("B"), opens))
        assertFalse(lottery.joinWaitlist(entrant("C"), opens))
        assertEquals(listOf("A", "B"), lottery.waitlist)
    }

    @Test
    fun negativeWaitlistCapacityIsInvalid() {
        val campsite = Campsite(name = "TestCampsite", occupancy = 4)
        assertFalse(campsite.createLottery(1, -1, emptyList()))
    }

    @Test
    fun emptyCapacityInputCanRepresentUnlimited() {
        // Tests recommended FORM parsing; verify the Compose form uses this rule.
        val input = ""
        val parsedCapacity = if (input.isBlank()) 0 else input.toIntOrNull()
        assertEquals(0, parsedCapacity)
        assertNull("Invalid nonblank capacity should not parse", "abc".toIntOrNull())
    }

    // ----- US 02.04.05: number of winners -----

    @Test
    fun acceptsWinnerCountAtOccupancyLimit() {
        val campsite = Campsite(name = "TestCampsite", occupancy = 4)
        assertTrue(campsite.createLottery(4, 0, emptyList()))
    }

    @Test
    fun rejectsWinnerCountHigherThanOccupancy() {
        val campsite = Campsite(name = "TestCampsite", occupancy = 2)

        // This will FAIL unless createLottery checks:
        // if (numWinners < 1 || numWinners > occupancy) return false
        assertFalse(campsite.createLottery(3, 0, emptyList()))
    }

    @Test
    fun rejectsZeroAndNegativeWinnerCounts() {
        val campsite = Campsite(name = "TestCampsite", occupancy = 4)
        assertFalse(campsite.createLottery(0, 0, emptyList()))
        assertFalse(campsite.createLottery(-1, 0, emptyList()))
    }

    @Test
    fun emptyDecimalAndTextWinnerInputsAreInvalid() {
        for (input in listOf("", "2.5", "abc", "-1.2")) {
            assertNull(input.toIntOrNull())
        }
    }

    // ----- Terms and policies -----

    @Test
    fun rejectsBlankTermsAndAcceptsNonBlankTerms() {
        val campsite = Campsite(name = "TestCampsite")
        assertFalse(campsite.setTermsAndConditions("  "))
        assertTrue(campsite.setTermsAndConditions("Respect quiet hours"))
        assertEquals("Respect quiet hours", campsite.termsAndConditions)
    }
}
