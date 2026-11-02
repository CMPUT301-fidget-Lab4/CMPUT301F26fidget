
package com.example.myapplication

import com.example.myapplication.features.Campsite
import com.example.myapplication.features.CampsiteRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.time.LocalDate

class CampsiteTest {

    private lateinit var camp: Campsite

    // Helper for dates in November 2026
    private fun nov(day: Int): LocalDate {
        return LocalDate.of(2026, 11, day)
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
        val result = camp.createLottery(2, -1, emptyList())

        assertFalse(result)
        assertFalse(camp.inLottery)
    }

    @Test
    fun createLottery_valid_setsLotteryDetails() {
        val result = camp.createLottery(2, 50, emptyList())

        assertTrue(result)
        assertTrue(camp.inLottery)
        assertEquals(2, camp.lottery.numWinners)
        assertEquals(50, camp.lottery.waitlistCapacity)
        assertEquals(listOf("Lakeview"), camp.lottery.campsiteIds)
        assertFalse(camp.lottery.drawn)
        assertTrue(camp.lottery.waitlist.isEmpty())
        assertTrue(camp.lottery.winners.isEmpty())
    }

    @Test
    fun createLottery_zeroCapacity_isAllowed() {
        val result = camp.createLottery(2, 0, emptyList())

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
    fun setReservation_startAfterEnd_returnsFalse() {
        val result = camp.setReservation(nov(8), nov(1))

        assertFalse(result)
        assertFalse(camp.reservationSet)
    }

    @Test
    fun setReservation_validDates_updatesRegistrationPeriod() {
        val result = camp.setReservation(nov(1), nov(8))

        assertTrue(result)
        assertTrue(camp.reservationSet)
        assertEquals(nov(1), camp.lottery.period.registrationStart)
        assertEquals(nov(8), camp.lottery.period.registrationEnd)
    }

    @Test
    fun setReservation_sameDay_isAllowed() {
        val result = camp.setReservation(nov(5), nov(5))

        assertTrue(result)
        assertTrue(camp.reservationSet)
    }

    @Test
    fun setReservation_endAfterBookingStart_returnsFalse() {
        camp.setReservation(nov(1), nov(8))
        camp.setBookingPeriod(nov(15), nov(29))

        val result = camp.setReservation(nov(1), nov(20))

        assertFalse(result)
        assertEquals(nov(8), camp.lottery.period.registrationEnd)
    }

    @Test
    fun setReservation_endOnBookingStart_isAllowed() {
        camp.setReservation(nov(1), nov(8))
        camp.setBookingPeriod(nov(15), nov(29))

        val result = camp.setReservation(nov(1), nov(15))

        assertTrue(result)
        assertEquals(nov(15), camp.lottery.period.registrationEnd)
    }

    // ---------- setBookingPeriod ----------

    @Test
    fun setBookingPeriod_noReservation_returnsFalse() {
        val result = camp.setBookingPeriod(nov(15), nov(29))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startAfterEnd_returnsFalse() {
        camp.setReservation(nov(1), nov(8))

        val result = camp.setBookingPeriod(nov(29), nov(15))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startsBeforeRegistrationEnds_returnsFalse() {
        camp.setReservation(nov(1), nov(8))

        val result = camp.setBookingPeriod(nov(5), nov(29))

        assertFalse(result)
        assertFalse(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_startsOnRegistrationEnd_isAllowed() {
        camp.setReservation(nov(1), nov(8))

        val result = camp.setBookingPeriod(nov(8), nov(29))

        assertTrue(result)
        assertTrue(camp.bookingSet)
    }

    @Test
    fun setBookingPeriod_validDates_updatesPeriod() {
        camp.setReservation(nov(1), nov(8))

        val result = camp.setBookingPeriod(nov(15), nov(29))

        assertTrue(result)
        assertTrue(camp.bookingSet)
        assertEquals(nov(15), camp.lottery.period.bookingStart)
        assertEquals(nov(29), camp.lottery.period.bookingEnd)
    }

    @Test
    fun setBookingPeriod_sameDay_isAllowed() {
        camp.setReservation(nov(1), nov(8))

        val result = camp.setBookingPeriod(nov(15), nov(15))

        assertTrue(result)
        assertEquals(nov(15), camp.lottery.period.bookingStart)
        assertEquals(nov(15), camp.lottery.period.bookingEnd)
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
