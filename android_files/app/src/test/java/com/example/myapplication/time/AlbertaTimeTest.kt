package com.example.myapplication.time

import com.example.myapplication.core.datetime.AlbertaTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests for AlbertaTime.
 *
 * Alberta uses a fixed UTC-06:00 offset all year, so these tests check the offset itself,
 * that it never changes in summer or winter, and that date and time conversions come out right.
 */
class AlbertaTimeTest {

    /** The zone should be exactly UTC-06:00. */
    @Test
    fun zoneIsMinusSixHours() {
        assertEquals(ZoneOffset.ofHours(-6), AlbertaTime.zone)
        assertEquals(-6 * 60 * 60, AlbertaTime.zone.totalSeconds)
        assertEquals("-06:00", AlbertaTime.zone.id)
    }

    /** Midnight UTC is 6 PM the day before in Alberta. */
    @Test
    fun midnightUtcIsSixPmThePreviousDay() {
        val time = LocalDateTime.ofInstant(Instant.parse("2026-11-12T00:00:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 0), time)
    }

    /** 06:00 UTC is exactly midnight in Alberta. */
    @Test
    fun sixAmUtcIsMidnightInAlberta() {
        val time = LocalDateTime.ofInstant(Instant.parse("2026-11-12T06:00:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0), time)
    }

    /** One minute before that is still the previous day. */
    @Test
    fun oneMinuteBeforeMidnightStaysOnThePreviousDay() {
        val time = LocalDateTime.ofInstant(Instant.parse("2026-11-12T05:59:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2026, 11, 11, 23, 59), time)
    }

    /** The clocks do not change in November, so the hour does not repeat or jump. */
    @Test
    fun novemberDoesNotFallBackAnHour() {
        val before = LocalDateTime.ofInstant(Instant.parse("2026-11-01T07:30:00Z"), AlbertaTime.zone)
        val after = LocalDateTime.ofInstant(Instant.parse("2026-11-01T08:30:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2026, 11, 1, 1, 30), before)
        assertEquals(LocalDateTime.of(2026, 11, 1, 2, 30), after)
    }

    /** The clocks do not change in March either, so there is no skipped hour. */
    @Test
    fun marchDoesNotSpringForwardAnHour() {
        val before = LocalDateTime.ofInstant(Instant.parse("2027-03-14T08:30:00Z"), AlbertaTime.zone)
        val after = LocalDateTime.ofInstant(Instant.parse("2027-03-14T09:30:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2027, 3, 14, 2, 30), before)
        assertEquals(LocalDateTime.of(2027, 3, 14, 3, 30), after)
    }

    /** Winter and summer dates use the same offset. */
    @Test
    fun summerAndWinterUseTheSameOffset() {
        val winter = Instant.parse("2027-01-15T12:00:00Z").atZone(AlbertaTime.zone)
        val summer = Instant.parse("2027-07-15T12:00:00Z").atZone(AlbertaTime.zone)

        assertEquals(ZoneOffset.ofHours(-6), winter.offset)
        assertEquals(ZoneOffset.ofHours(-6), summer.offset)
        assertEquals(6, winter.hour)
        assertEquals(6, summer.hour)
    }

    /** Every month of 2027 has the same offset. */
    @Test
    fun offsetNeverChangesDuringTheYear() {
        for (month in 1..12) {
            val instant = LocalDateTime.of(2027, month, 15, 12, 0).toInstant(ZoneOffset.UTC)

            assertEquals(ZoneOffset.ofHours(-6), instant.atZone(AlbertaTime.zone).offset)
        }
    }

    /** Turning a local time into an instant and back gives the same local time. */
    @Test
    fun convertingToInstantAndBackKeepsTheLocalTime() {
        val local = LocalDateTime.of(2027, 6, 30, 14, 45)

        val instant = local.atZone(AlbertaTime.zone).toInstant()
        val backAgain = LocalDateTime.ofInstant(instant, AlbertaTime.zone)

        assertEquals(local, backAgain)
    }

    /** A local Alberta time converts to the right UTC instant. */
    @Test
    fun localTimeConvertsToTheCorrectUtcInstant() {
        val local = LocalDateTime.of(2026, 11, 11, 18, 0)

        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), local.atZone(AlbertaTime.zone).toInstant())
    }

    /** LocalDateTime.now with a fixed clock uses the Alberta offset. */
    @Test
    fun nowUsesTheAlbertaOffsetWithAFixedClock() {
        val clock = Clock.fixed(Instant.parse("2027-02-03T03:15:00Z"), AlbertaTime.zone)

        assertEquals(LocalDateTime.of(2027, 2, 2, 21, 15), LocalDateTime.now(clock))
    }
}