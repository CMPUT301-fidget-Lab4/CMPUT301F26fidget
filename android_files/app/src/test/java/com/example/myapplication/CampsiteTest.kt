//
//package com.example.myapplication
//
//import com.example.myapplication.features.Campsite
//import com.example.myapplication.features.Lottery
//import com.example.myapplication.features.BookingSchedule
//import com.google.firebase.firestore.Exclude
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Test
//
//class CampsiteTest {
//
//    private lateinit var camp: Campsite
//
//    @Before
//    fun setUp() {
//        camp = Campsite(
//            name = "Lakeview",
//            occupancy = 4
//        )
//    }
//
//    // ---------- Initialization ----------
//
//    @Test
//    fun campsite_validInitialization_storesDetails() {
//        assertEquals("Lakeview", camp.name)
//        assertEquals(4, camp.occupancy)
//        assertEquals(0.0, camp.rating, 0.0)
//        assertEquals("", camp.termsAndConditions)
//    }
//
//    @Test
//    fun campsite_defaultValues_areCorrect() {
//        val campsite = Campsite()
//
//        assertEquals("", campsite.name)
//        assertNull(campsite.location)
//        assertEquals("", campsite.description)
//        assertEquals(0, campsite.occupancy)
//        assertEquals("", campsite.photo)
//        assertEquals("", campsite.tags)
//        assertEquals("", campsite.ownerID)
//        assertEquals(0.0, campsite.rating, 0.0)
//        assertEquals("", campsite.termsAndConditions)
//    }
//
//    @Test
//    fun campsite_allConstructorValues_areStored() {
//        val campsite = Campsite(
//            name = "Pinewood",
//            description = "Forest campsite",
//            occupancy = 5,
//            photo = "photo.jpg",
//            tags = "forest",
//            ownerID = "owner123",
//            rating = 4.5,
//            termsAndConditions = "No fires."
//        )
//
//        assertEquals("Pinewood", campsite.name)
//        assertEquals("Forest campsite", campsite.description)
//        assertEquals(5, campsite.occupancy)
//        assertEquals("photo.jpg", campsite.photo)
//        assertEquals("forest", campsite.tags)
//        assertEquals("owner123", campsite.ownerID)
//        assertEquals(4.5, campsite.rating, 0.0)
//        assertEquals("No fires.", campsite.termsAndConditions)
//    }
//
//    // ---------- Lottery Collection ----------
//
//    @Test
//    fun campsite_lotteryCollection_initiallyNull() {
//        assertNull(camp.lotteryCollection)
//    }
//
//    @Test
//    fun campsite_lotteryCollection_canBeAssigned() {
//        val lotteries = listOf<Lottery>()
//
//        camp.lotteryCollection = lotteries
//
//        assertNotNull(camp.lotteryCollection)
//        assertSame(lotteries, camp.lotteryCollection)
//        assertTrue(camp.lotteryCollection!!.isEmpty())
//    }
//
//    @Test
//    fun campsite_lotteryCollection_canBeResetToNull() {
//        camp.lotteryCollection = listOf<Lottery>()
//
//        camp.lotteryCollection = null
//
//        assertNull(camp.lotteryCollection)
//    }
//
//    // ---------- Booking Schedules ----------
//
//    @Test
//    fun campsite_bookingSchedules_initiallyNull() {
//        assertNull(camp.bookingSchedules)
//    }
//
//    @Test
//    fun campsite_bookingSchedules_canBeAssigned() {
//        val schedules = mutableListOf<BookingSchedule>()
//
//        camp.bookingSchedules = schedules
//
//        assertNotNull(camp.bookingSchedules)
//        assertSame(schedules, camp.bookingSchedules)
//        assertTrue(camp.bookingSchedules!!.isEmpty())
//    }
//
//    @Test
//    fun campsite_bookingSchedules_canBeResetToNull() {
//        camp.bookingSchedules = mutableListOf<BookingSchedule>()
//
//        camp.bookingSchedules = null
//
//        assertNull(camp.bookingSchedules)
//    }
//
//    // ---------- In Lottery ----------
//
//    @Test
//    fun campsite_inLottery_initiallyFalse() {
//        assertFalse(camp.inLottery)
//    }
//
//    @Test
//    fun campsite_inLottery_canBeSetTrue() {
//        camp.inLottery = true
//
//        assertTrue(camp.inLottery)
//    }
//
//    @Test
//    fun campsite_inLottery_canBeSetFalse() {
//        camp.inLottery = true
//        camp.inLottery = false
//
//        assertFalse(camp.inLottery)
//    }
//
//    // ---------- Rating ----------
//
//    @Test
//    fun campsite_rating_initiallyZero() {
//        assertEquals(0.0, camp.rating, 0.0)
//    }
//
//    @Test
//    fun campsite_rating_canBeUpdated() {
//        camp.rating = 4.5
//
//        assertEquals(4.5, camp.rating, 0.0)
//    }
//
//    // ---------- Terms And Conditions ----------
//
//    @Test
//    fun campsite_termsAndConditions_initiallyEmpty() {
//        assertEquals("", camp.termsAndConditions)
//    }
//
//    @Test
//    fun campsite_termsAndConditions_canBeUpdated() {
//        camp.termsAndConditions = "No fires."
//
//        assertEquals("No fires.", camp.termsAndConditions)
//    }
//
//    @Test
//    fun campsite_termsAndConditions_canBeReplaced() {
//        camp.termsAndConditions = "No fires."
//        camp.termsAndConditions = "Pets allowed."
//
//        assertEquals("Pets allowed.", camp.termsAndConditions)
//    }
//
//    // ---------- Firestore Exclusion ----------
//
//    @Test
//    fun campsite_lotteryCollection_excludedFromFirestore() {
//        val getter = Campsite::class.java
//            .getMethod("getLotteryCollection")
//
//        assertNotNull(
//            getter.getAnnotation(Exclude::class.java)
//        )
//    }
//
//    @Test
//    fun campsite_bookingSchedules_excludedFromFirestore() {
//        val getter = Campsite::class.java
//            .getMethod("getBookingSchedules")
//
//        assertNotNull(
//            getter.getAnnotation(Exclude::class.java)
//        )
//    }
//
//    @Test
//    fun campsite_inLottery_excludedFromFirestore() {
//        val getter = Campsite::class.java
//            .getMethod("getInLottery")
//
//        assertNotNull(
//            getter.getAnnotation(Exclude::class.java)
//        )
//    }
//}
