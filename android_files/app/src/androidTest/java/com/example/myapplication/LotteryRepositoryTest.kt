package com.example.myapplication.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Simple local tests for the existing Lottery and Registration models.
 *
 * Put this one file in app/src/test/java/com/example/myapplication/features/
 * Run LotteryOfflineTest as a local JUnit test.
 *
 * No Firebase, MockK, emulator, or other test helper files required.
 * These tests demonstrate in-memory lottery changes; they do NOT call
 * LotteryRepository or verify Firebase writes, permissions, or transactions.
 */
class LotteryOfflineTest {

    private val start = LocalDateTime.of(2026, 12, 1, 9, 0)
    private val end = LocalDateTime.of(2026, 12, 10, 17, 0)

    private fun exampleLottery() = Lottery(
        id = "lottery-1",
        campsiteIds = listOf("Pine", "Lake"),
        period = Registration(start, end),
        numWinners = 2,
        waitlistCapacity = 3,
        waitlist = listOf("user1"),
        winners = emptyList(),
        drawn = false
    )

    @Test
    fun registrationIsOpenBetweenStartAndEnd() {
        val registration = Registration(start, end)
        assertFalse(registration.registrationIsOpen(start.minusSeconds(1)))
        assertTrue(registration.registrationIsOpen(start))
        assertTrue(registration.registrationIsOpen(start.plusDays(1)))
        assertFalse(registration.registrationIsOpen(end))
    }

    @Test
    fun changePeriodOnly() {
        val original = exampleLottery()
        val newStart = start.plusDays(15)
        val newEnd = end.plusDays(15)
        val updated = original.copy(period = Registration(newStart, newEnd))

        assertEquals(newStart, updated.period.registrationStart)
        assertEquals(newEnd, updated.period.registrationEnd)
        assertEquals(original.numWinners, updated.numWinners)
        assertEquals(original.waitlist, updated.waitlist)
    }

    @Test
    fun changeWaitlistCapacityOnly() {
        val original = exampleLottery()
        val updated = original.copy(waitlistCapacity = 0)

        assertEquals(0, updated.waitlistCapacity) // 0 = unlimited by repository convention
        assertEquals(3, original.waitlistCapacity)
        assertEquals(original.campsiteIds, updated.campsiteIds)
    }

    @Test
    fun changeNumberOfWinnersOnly() {
        val original = exampleLottery()
        val updated = original.copy(numWinners = 1)

        assertEquals(1, updated.numWinners)
        assertEquals(2, original.numWinners)
        assertEquals(original.period.registrationEnd, updated.period.registrationEnd)
    }

    @Test
    fun replaceWaitlistWithoutChangingOriginal() {
        val original = exampleLottery()
        val updated = original.copy(waitlist = listOf("user2", "user3"))

        assertEquals(listOf("user2", "user3"), updated.waitlist)
        assertEquals(listOf("user1"), original.waitlist)
    }

    @Test
    fun addCampsiteLocally() {
        val original = exampleLottery()
        val updated = original.copy(campsiteIds = original.campsiteIds + "Mountain")

        assertEquals(listOf("Pine", "Lake", "Mountain"), updated.campsiteIds)
        assertEquals(listOf("Pine", "Lake"), original.campsiteIds)
    }

    @Test
    fun removeCampsiteLocally() {
        val original = exampleLottery()
        val updated = original.copy(campsiteIds = original.campsiteIds - "Lake")

        assertEquals(listOf("Pine"), updated.campsiteIds)
        assertEquals(2, original.campsiteIds.size)
    }

    @Test
    fun drawDistinctWinnersLocally() {
        val original = exampleLottery().copy(
            waitlist = listOf("a", "b", "c", "d"),
            numWinners = 2
        )
        val selected = original.waitlist.distinct().shuffled(Random(42)).take(original.numWinners)
        val updated = original.copy(winners = selected, drawn = true)

        assertTrue(updated.drawn)
        assertEquals(2, updated.winners.size)
        assertEquals(2, updated.winners.distinct().size)
        assertTrue(updated.winners.all { it in original.waitlist })
        assertFalse(original.drawn)
    }
}
