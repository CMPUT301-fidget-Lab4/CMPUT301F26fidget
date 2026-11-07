package com.example.myapplication

import com.example.myapplication.features.BookingSchedule
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.CampsiteLocation
import com.example.myapplication.features.Lottery
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Local JUnit 4 tests for the Campsite model.
 *
 * These test model defaults, stored information, mutable properties,
 * related lotteries/booking schedules, and Firestore annotations.
 * They do not access Firebase or require an Android emulator.
 */
class CampsiteTest {

    private lateinit var campsite: Campsite

    @Before
    fun setUp() {
        campsite = Campsite(
            name = "Lakeview",
            description = "A quiet lakeside campsite",
            occupancy = 4,
            ownerID = "owner-123"
        )
    }

    private fun makeLottery(id: String = "lottery-1"): Lottery = Lottery(
        id = id,
        ownerId = "owner-123",
        campsiteIds = listOf("Lakeview"),
        numWinners = 2,
        waitlistCapacity = 10
    )

    private fun makeSchedule(): BookingSchedule<Lottery> = BookingSchedule(
        registration = makeLottery(),
        bookingStart = LocalDateTime.of(2035, 7, 1, 0, 0),
        bookingEnd = LocalDateTime.of(2035, 7, 8, 0, 0)
    )

    // -------------------- Construction --------------------

    @Test
    fun defaultConstructor_hasExpectedValues() {
        val emptyCampsite = Campsite()

        assertEquals("", emptyCampsite.name)
        assertNull(emptyCampsite.location)
        assertEquals("", emptyCampsite.description)
        assertEquals(0, emptyCampsite.occupancy)
        assertEquals("", emptyCampsite.photo)
        assertEquals("", emptyCampsite.tags)
        assertEquals("", emptyCampsite.ownerID)
        assertEquals(0.0, emptyCampsite.rating, 0.0001)
        assertEquals("", emptyCampsite.termsAndConditions)
        assertFalse(emptyCampsite.inLottery)
        assertNull(emptyCampsite.lotteryCollection)
        assertNull(emptyCampsite.bookingSchedules)
    }

    @Test
    fun constructor_storesAllProvidedDetails() {
        val coordinates = GeoPoint(53.5461, -113.4937)
        val location = CampsiteLocation(
            address = "Edmonton, Alberta",
            geoPoint = coordinates
        )

        val fullCampsite = Campsite(
            name = "Pine Ridge",
            location = location,
            description = "Forest site with a fire pit",
            occupancy = 6,
            photo = "photo-reference",
            tags = "forest, hiking",
            ownerID = "device-456",
            rating = 4.5,
            termsAndConditions = "No fires after 10 PM"
        )

        assertEquals("Pine Ridge", fullCampsite.name)
        assertSame(location, fullCampsite.location)
        assertEquals("Edmonton, Alberta", fullCampsite.location?.address)
        assertEquals(coordinates, fullCampsite.location?.geoPoint)
        assertEquals("Forest site with a fire pit", fullCampsite.description)
        assertEquals(6, fullCampsite.occupancy)
        assertEquals("photo-reference", fullCampsite.photo)
        assertEquals("forest, hiking", fullCampsite.tags)
        assertEquals("device-456", fullCampsite.ownerID)
        assertEquals(4.5, fullCampsite.rating, 0.0001)
        assertEquals("No fires after 10 PM", fullCampsite.termsAndConditions)
    }

    @Test
    fun constructor_withOnlyRequiredDetails_usesDefaultsForOtherFields() {
        assertEquals("Lakeview", campsite.name)
        assertEquals("A quiet lakeside campsite", campsite.description)
        assertEquals(4, campsite.occupancy)
        assertEquals("owner-123", campsite.ownerID)
        assertNull(campsite.location)
        assertEquals("", campsite.photo)
        assertEquals("", campsite.tags)
        assertEquals(0.0, campsite.rating, 0.0001)
    }

    // -------------------- Mutable Details --------------------

    @Test
    fun rating_canBeUpdatedMoreThanOnce() {
        campsite.rating = 3.5
        assertEquals(3.5, campsite.rating, 0.0001)

        campsite.rating = 4.75
        assertEquals(4.75, campsite.rating, 0.0001)
    }

    @Test
    fun termsAndConditions_canBeAddedAndReplaced() {
        campsite.termsAndConditions = "No pets"
        assertEquals("No pets", campsite.termsAndConditions)

        campsite.termsAndConditions = "Pets allowed on leash"
        assertEquals("Pets allowed on leash", campsite.termsAndConditions)
    }

    @Test
    fun termsAndConditions_canBeCleared() {
        campsite.termsAndConditions = "Quiet hours after 10 PM"
        campsite.termsAndConditions = ""

        assertEquals("", campsite.termsAndConditions)
    }

    // -------------------- Lottery State --------------------

    @Test
    fun inLottery_startsFalseAndCanBeToggled() {
        assertFalse(campsite.inLottery)

        campsite.inLottery = true
        assertTrue(campsite.inLottery)

        campsite.inLottery = false
        assertFalse(campsite.inLottery)
    }

    @Test
    fun lotteryCollection_startsNull() {
        assertNull(campsite.lotteryCollection)
    }

    @Test
    fun lotteryCollection_canStoreMultipleLotteries() {
        val first = makeLottery("lottery-1")
        val second = makeLottery("lottery-2")
        val lotteries = listOf(first, second)

        campsite.lotteryCollection = lotteries

        assertSame(lotteries, campsite.lotteryCollection)
        assertEquals(2, campsite.lotteryCollection?.size)
        assertEquals(
            listOf("lottery-1", "lottery-2"),
            campsite.lotteryCollection?.map { it.id }
        )
        assertTrue(campsite.lotteryCollection!!.contains(first))
    }

    @Test
    fun lotteryCollection_canBeReplacedAndCleared() {
        campsite.lotteryCollection = listOf(makeLottery("old"))
        campsite.lotteryCollection = listOf(makeLottery("new"))

        assertEquals("new", campsite.lotteryCollection!!.single().id)

        campsite.lotteryCollection = null
        assertNull(campsite.lotteryCollection)
    }

    @Test
    fun changingLotteryCollection_doesNotAutomaticallyChangeInLottery() {
        campsite.lotteryCollection = listOf(makeLottery())
        assertFalse(campsite.inLottery)

        campsite.inLottery = true
        campsite.lotteryCollection = null
        assertTrue(campsite.inLottery)
    }

    // -------------------- Booking Schedules --------------------

    @Test
    fun bookingSchedules_startsNull() {
        assertNull(campsite.bookingSchedules)
    }

    @Test
    fun bookingSchedules_canStoreARealSchedule() {
        val schedule = makeSchedule()
        val schedules: MutableList<BookingSchedule<*>> = mutableListOf(schedule)

        campsite.bookingSchedules = schedules

        assertNotNull(campsite.bookingSchedules)
        assertSame(schedules, campsite.bookingSchedules)
        assertSame(schedule, campsite.bookingSchedules!!.single())
        assertEquals("lottery-1", schedule.registration.id)
    }

    @Test
    fun bookingSchedules_canGrowAndShrink() {
        val schedules: MutableList<BookingSchedule<*>> = mutableListOf()
        campsite.bookingSchedules = schedules

        val first = makeSchedule()
        val second = BookingSchedule(
            registration = makeLottery("lottery-2"),
            bookingStart = LocalDateTime.of(2035, 8, 1, 0, 0),
            bookingEnd = LocalDateTime.of(2035, 8, 8, 0, 0)
        )

        schedules.add(first)
        schedules.add(second)
        assertEquals(2, campsite.bookingSchedules!!.size)
        assertSame(second, campsite.bookingSchedules!![1])

        schedules.remove(first)
        assertEquals(1, campsite.bookingSchedules!!.size)
        assertSame(second, campsite.bookingSchedules!!.single())
    }

    @Test
    fun bookingSchedules_canBeResetToNull() {
        campsite.bookingSchedules = mutableListOf(makeSchedule())
        campsite.bookingSchedules = null

        assertNull(campsite.bookingSchedules)
    }

    @Test
    fun storedBookingSchedule_retainsBookings() {
        val schedule = makeSchedule()
        campsite.bookingSchedules = mutableListOf(schedule)

        val booking = schedule.Booking(
            entrantId = "entrant-1",
            campsiteId = campsite.name,
            startDate = LocalDateTime.of(2035, 7, 2, 0, 0),
            endDate = LocalDateTime.of(2035, 7, 4, 0, 0)
        )

        assertTrue(schedule.addBooking(booking, occupancy = campsite.occupancy))
        assertEquals(1, schedule.getBookings().size)
        assertSame(schedule, campsite.bookingSchedules!!.single())
        assertSame(booking, schedule.getBookings().single())
    }

    // -------------------- Object Independence --------------------

    @Test
    fun updatingOneCampsite_doesNotChangeAnother() {
        val another = Campsite(name = "Pine Ridge", occupancy = 2)

        campsite.rating = 4.8
        campsite.termsAndConditions = "No pets"
        campsite.inLottery = true
        campsite.lotteryCollection = listOf(makeLottery())
        campsite.bookingSchedules = mutableListOf(makeSchedule())

        assertEquals(0.0, another.rating, 0.0001)
        assertEquals("", another.termsAndConditions)
        assertFalse(another.inLottery)
        assertNull(another.lotteryCollection)
        assertNull(another.bookingSchedules)
        assertEquals(2, another.occupancy)
    }

    // -------------------- Firestore Annotations --------------------

    @Test
    fun lotteryCollection_getterIsExcludedFromFirestore() {
        val getter = Campsite::class.java.getMethod("getLotteryCollection")
        assertTrue(getter.isAnnotationPresent(Exclude::class.java))
    }

    @Test
    fun bookingSchedules_getterIsExcludedFromFirestore() {
        val getter = Campsite::class.java.getMethod("getBookingSchedules")
        assertTrue(getter.isAnnotationPresent(Exclude::class.java))
    }

    @Test
    fun inLottery_getterIsNotExcludedFromFirestore() {
        val getter = Campsite::class.java.getMethod("getInLottery")
        assertFalse(getter.isAnnotationPresent(Exclude::class.java))
    }
}
