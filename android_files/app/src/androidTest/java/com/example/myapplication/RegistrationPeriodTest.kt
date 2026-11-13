
package com.example.myapplication.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class RegistrationTest {

    private lateinit var registration: Registration

    private val start = LocalDateTime.of(2026, 11, 5, 10, 0)
    private val end = LocalDateTime.of(2026, 11, 10, 10, 0)

    @Before
    fun setUp() {
        registration = Registration(
            registrationStart = start,
            registrationEnd = end
        )
    }

    @Test
    fun registrationIsOpenDuringRegistrationPeriod() {
        val now = LocalDateTime.of(2026, 11, 7, 12, 0)

        assertTrue(registration.registrationIsOpen(now))
    }

    @Test
    fun registrationIsClosedBeforeStart() {
        val now = LocalDateTime.of(2026, 11, 4, 10, 0)

        assertFalse(registration.registrationIsOpen(now))
    }

    @Test
    fun registrationIsOpenAtExactStart() {
        assertTrue(registration.registrationIsOpen(start))
    }

    @Test
    fun registrationIsClosedAtExactEnd() {
        assertFalse(registration.registrationIsOpen(end))
    }

    @Test
    fun registrationIsClosedAfterEnd() {
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)

        assertFalse(registration.registrationIsOpen(now))
    }

    @Test
    fun registrationIsClosedWhenStartEqualsEnd() {
        registration.registrationEnd = start

        assertFalse(registration.registrationIsOpen(start))
    }

    @Test
    fun registrationIsClosedWhenEndIsBeforeStart() {
        registration.registrationEnd = start.minusDays(1)

        val now = start.minusHours(12)

        assertFalse(registration.registrationIsOpen(now))
    }
}
