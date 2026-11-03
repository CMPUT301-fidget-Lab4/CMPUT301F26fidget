package com.example.myapplication

import com.example.myapplication.features.Campsite
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class CampsiteTest {

    private lateinit var camp: Campsite

    // Fixed starting point (tomorrow) so every date is in the future
    // and the same day(n) call always gives the same value.
    private val base: LocalDateTime = LocalDateTime.now().plusDays(1)

    private fun day(n: Long): LocalDateTime {
        return base.plusDays(n)
    }

    // Reservation and booking need a lottery to exist first
    private fun startLottery() {
        camp.createLottery(1, 10, emptyList())
    }

    @Before
    fun setUp() {
        camp = Campsite(name = "Lakeview", occupancy = 4)
    }

    // ---------- createLottery ----------

    @Test
    fun createLottery_zeroWinners_returnsFalse() {
        val result = camp.createLottery(0, 10, emptyList())

        assertFalse(result)
        assertFalse(camp.inLottery)
    }

    @Test
    fun createLottery_negativeWinners_returnsFalse() {
        val result = camp.createLottery(-2, 10, emptyList())

        assertFalse(result)
        assertFalse(camp.inLottery)
    }

    @Test
    fun createLottery_negativeCapacity_returnsFalse() {
        val result = camp.createLottery(1, -1, emptyList())

        assertFalse(result)
        assertFalse(camp.inLottery)
    }

    @Test
    fun createLottery_moreWinnersThanCampsites_returnsFalse() {
        // Only one campsite, so only one winner is possible
        val result = camp.createLottery(2, 10, emptyList())

        assertFalse(result)
        assertFalse(camp.inLottery)
    }

    @Test
    fun createLottery_alreadyExists_returnsFalse() {
        camp.createLottery(1, 10, emptyList())

        val result = camp.createLottery(1, 20, emptyList())

        assertFalse(result)
        assertEquals(10, camp.lottery.waitlistCapacity)
    }

    @Test
    fun createLottery_valid_setsLotteryDetails() {
        val result = camp.createLottery(1, 50, emptyList())

        assertTrue(result)
        assertTrue(camp.inLottery)
        assertEquals(1, camp.lottery.numWinners)
        assertEquals(50, camp.lottery.waitlistCapacity)
        assertEquals(listOf("Lakeview"), camp.lottery.campsiteIds)
        assertFalse(camp.lottery.drawn)
        assertTrue(camp.lottery.waitlist.isEmpty())
        assertTrue(camp.lottery.winners.isEmpty())
    }

    @Test
    fun createLottery_zeroCapacity_isAllowed() {
        val result = camp.createLottery(1, 0, emptyList())

        assertTrue(result)
        assertEquals(0, camp.lottery.waitlistCapacity)
    }

    @Test
    fun createLottery_multipleCampsites_storesAllNames() {
        val others = listOf("Pinewood", "Riverside")

        val result = camp.createLottery(3, 20, others)

        assertTrue(result)
        assertEquals(
            listOf("Lakeview", "Pinewood", "Riverside"),
            camp.lottery.campsiteIds
        )
    }

    @Test
    fun createLottery_duplicateCampsites_removesDuplicates() {
        val others = listOf(
            "Lakeview",
            "Pinewood",
            "Pinewood"
        )

        val result = camp.createLottery(1, 10, others)

        assertTrue(result)
        assertEquals(
            listOf("Lakeview", "Pinewood"),
            camp.lottery.campsiteIds
        )
    }

    @Test
    fun createLottery_valid_generatesId() {
        val result = camp.createLottery(1, 10, emptyList())

        assertTrue(result)
        assertTrue(camp.lottery.id.isNotEmpty())
    }

    // ---------- setReservation ----------

    @Test
    fun setReservation_noLottery_returnsFalse() {
        val result = camp.setReservation(day(1), day(8))

        assertFalse(result)
        assertFalse(camp.reservationSet)
    }

    @Test
    fun setReservation_startInPast_returnsFalse() {
        startLottery()

        val result = camp.setReservation(
            LocalDateTime.now().minusDays(1),
            day(8)
        )

        assertFalse(result)
        assertFalse(camp.reservationSet)
    }

    @Test
    fun setReservation_startAfterEnd_returnsFalse() {
        startLottery()

        val result = camp.setReservation(day(8), day(1))

        assertFalse(result)
        assertFalse(camp.reservationSet)
    }

    @Test
    fun setReservation_validDates_updatesRegistrationPeriod() {
        startLottery()

        val result = camp.setReservation(day(1), day(8))

        assertTrue(result)
        assertTrue(camp.reservationSet)
        assertEquals(day(1), camp.lottery.period.registrationStart)
        assertEquals(day(8), camp.lottery.period.registrationEnd)
    }

    @Test
    fun setReservation_sameStartAndEnd_returnsFalse() {
        startLottery()

        val result = camp.setReservation(day(5), day(5))

        assertFalse(result)
        assertFalse(camp.reservationSet)
    }

    @Test
    fun setReservation_endAfterBookingStart_returnsFalse() {
        startLottery()
        camp.setReservation(day(1), day(8))
        camp.setBookingPeriod(day(15), day(29))

        val result = camp.setReservation(day(1), day(20))

        assertFalse(result)
        assertEquals(day(8), camp.lottery.period.registrationEnd)
    }

    @Test
    fun setReservation_endOnBookingStart_isAllowed() {
        startLottery()
        camp.setReservation(day(1), day(8))
        camp.setBookingPeriod(day(15), day(29))

        val result = camp.setReservation(day(1), day(15))

        assertTrue(result)
        assertEquals(day(15), camp.lottery.period.registrationEnd)
    }

    // ---------- setBookingPeriod ----------

    @Test
    fun setBookingPeriod_noReservation_returnsFalse() {
        startLottery()

        val result = camp.setBookingPeriod(day(15), day(29))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startAfterEnd_returnsFalse() {
        startLottery()
        camp.setReservation(day(1), day(8))

        val result = camp.setBookingPeriod(day(29), day(15))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startsBeforeRegistrationEnds_returnsFalse() {
        startLottery()
        camp.setReservation(day(1), day(8))

        val result = camp.setBookingPeriod(day(5), day(29))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startsOnRegistrationEnd_isAllowed() {
        startLottery()
        camp.setReservation(day(1), day(8))

        val result = camp.setBookingPeriod(day(8), day(29))

        assertTrue(result)
        assertTrue(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_validDates_updatesPeriod() {
        startLottery()
        camp.setReservation(day(1), day(8))

        val result = camp.setBookingPeriod(day(15), day(29))

        assertTrue(result)
        assertTrue(camp.bookingSet)
        assertEquals(day(15), camp.lottery.period.bookingStart)
        assertEquals(day(29), camp.lottery.period.bookingEnd)
    }

    @Test
    fun setBookingPeriod_sameStartAndEnd_returnsFalse() {
        startLottery()
        camp.setReservation(day(1), day(8))

        val result = camp.setBookingPeriod(day(15), day(15))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    // ---------- setTermsAndConditions ----------

    @Test
    fun setTermsAndConditions_blank_returnsFalse() {
        val result = camp.setTermsAndConditions("   ")

        assertFalse(result)
        assertEquals("", camp.termsAndConditions)
    }

    @Test
    fun setTermsAndConditions_validText_updatesTerms() {
        val result = camp.setTermsAndConditions("No fires.")

        assertTrue(result)
        assertEquals("No fires.", camp.termsAndConditions)
    }
}