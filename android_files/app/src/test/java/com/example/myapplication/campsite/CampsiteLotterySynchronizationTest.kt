package com.example.myapplication.campsite

import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.data.firebase.CampsiteRepository
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.frontEnd.screens.campsiteViews.availabilityMessage
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

/** Both real repositories react to simulated Firestore snapshots; no repository logic is mocked. */
class CampsiteLotterySynchronizationTest {
    @Test fun aScheduleFailureNeverLooksVerifiedAndRetryReplacesThePreviousListener() {
        Fixture().use { f ->
            assertTrue(f.campsites.bookingSchedules.checking)
            assertNotNull(f.campsites.bookingSchedules.availabilityMessage())
            f.failSchedules()
            assertNotNull(f.campsites.bookingSchedules.error)
            assertNull(f.campsites.bookingSchedules.schedules)
            assertTrue(f.campsites.bookingSchedules.availabilityMessage()?.contains("Couldn't verify") == true)

            val previousListener = f.latestScheduleListener()
            f.campsites.retryBookingSchedules()
            assertTrue(f.campsites.bookingSchedules.checking)
            f.emitSchedules(cached = true)
            assertTrue(f.campsites.bookingSchedules.fromCache)
            assertNotNull(f.campsites.bookingSchedules.availabilityMessage())
            f.emitSchedules()
            assertNull(f.campsites.bookingSchedules.availabilityMessage())
            assertEquals(setOf("a", "b"), f.campsites.bookingSchedules.byCampsite?.keys)

            previousListener.onEvent(null, mock(FirebaseFirestoreException::class.java))
            assertNull(f.campsites.bookingSchedules.error)
            f.failSchedules()
            assertNotNull(f.campsites.bookingSchedules.error)
            assertTrue(f.campsites.bookingSchedules.availabilityMessage()?.contains("previously loaded") == true)
            assertEquals(setOf("a", "b"), f.campsites.bookingSchedules.byCampsite?.keys)
            f.assertOneListenerPerAttempt()
        }
    }

    @Test fun lotteryChangesAndRemovalRefreshListAndMapWhileRetainingSchedules() {
        Fixture().use { f ->
            f.emitCampsites("a", "b", "unrelated")
            f.emitSchedules()
            f.emitLottery(waitlist = listOf("entrant"))
            f.representations("a", "b").forEach { camp ->
                val lottery = camp.lotteryCollection!!.single()
                assertEquals("lot", lottery.id)
                assertEquals(listOf("entrant"), lottery.waitlistIds)
                assertFalse(lottery.drawn)
                assertEquals(listOf("lot"), camp.bookingSchedules!!.map { (it.registration as com.example.myapplication.features.lottery.Lottery).id })
            }

            f.emitLottery(waitlist = listOf("entrant", "second"), winners = listOf("second"), drawn = true)
            f.representations("a", "b").forEach { camp ->
                val lottery = camp.lotteryCollection!!.single()
                assertEquals(listOf("entrant", "second"), lottery.waitlistIds)
                assertEquals(listOf("second"), lottery.winners)
                assertTrue(lottery.drawn)
                assertEquals(listOf("lot"), camp.bookingSchedules!!.map { (it.registration as com.example.myapplication.features.lottery.Lottery).id })
            }

            f.emitNoLotteries()
            assertTrue(f.lotteries.allLotteries.isEmpty())
            f.representations("a", "b").forEach { camp ->
                assertEquals(emptyList<Any>(), camp.lotteryCollection)
                assertEquals(listOf("lot"), camp.bookingSchedules!!.map { (it.registration as com.example.myapplication.features.lottery.Lottery).id })
            }
            f.representations("unrelated").forEach { camp ->
                assertEquals(emptyList<Any>(), camp.lotteryCollection)
                assertTrue(camp.bookingSchedules!!.isEmpty())
            }
            assertEquals(setOf("a", "b", "unrelated"), f.campsites.campsitesMap.keys)
        }
    }

    @Test fun campsiteDeletionRemovesBothRepresentationsAndRefreshesSurvivingLotteryReferences() {
        Fixture().use { f ->
            f.emitCampsites("a", "b", "unrelated")
            f.emitSchedules()
            f.emitLottery(waitlist = listOf("entrant"))
            val removed = f.campsites.campsitesMap.getValue("a")
            val oldSurvivor = f.campsites.campsitesMap.getValue("b")
            assertSame(removed, f.lotteries.allLotteries.getValue("lot").campsites["a"])
            f.emitCampsites("b", "unrelated")

            assertFalse(f.campsites.campsitesMap.containsKey("a"))
            assertFalse(f.campsites.campsites.any { it.name == "a" })
            assertNull(f.campsites.findCampsite("a"))
            assertNotSame(oldSurvivor, f.campsites.campsitesMap.getValue("b"))
            val survivingLottery = f.lotteries.allLotteries.getValue("lot")
            assertTrue(survivingLottery.campsites.containsKey("a"))
            assertNull(survivingLottery.campsites["a"])
            assertSame(f.campsites.campsitesMap.getValue("b"), survivingLottery.campsites["b"])
            f.representations("b").forEach { camp ->
                assertSame(survivingLottery, camp.lotteryCollection!!.single())
                assertEquals(listOf("lot"), camp.bookingSchedules!!.map { (it.registration as com.example.myapplication.features.lottery.Lottery).id })
            }

            f.emitLottery(waitlist = listOf("entrant"), campsiteIds = listOf("b"))
            f.representations("b").forEach { camp ->
                assertEquals(listOf("b"), camp.lotteryCollection!!.single().campsiteIds)
                assertEquals(listOf("lot"), camp.bookingSchedules!!.map { (it.registration as com.example.myapplication.features.lottery.Lottery).id })
            }
            f.representations("unrelated").forEach { camp ->
                assertTrue(camp.lotteryCollection!!.isEmpty())
                assertTrue(camp.bookingSchedules!!.isEmpty())
            }
        }
    }

    private class Fixture : AutoCloseable {
        private val db = mock(FirebaseFirestore::class.java)
        private val campsiteQuery = mock(CollectionReference::class.java)
        private val scheduleQuery = mock(CollectionReference::class.java)
        private val lotteryQuery = mock(Query::class.java)
        private lateinit var campsiteListener: EventListener<QuerySnapshot>
        private lateinit var scheduleListener: EventListener<QuerySnapshot>
        private val scheduleRegistrations = mutableListOf<ListenerRegistration>()
        private lateinit var lotteryListener: EventListener<QuerySnapshot>
        val lotteries: LotteryRepository
        val campsites: CampsiteRepository

        init {
            `when`(db.collection("campsites")).thenReturn(campsiteQuery)
            `when`(db.collection("bookingSchedules")).thenReturn(scheduleQuery)
            `when`(db.collectionGroup("lottery")).thenReturn(lotteryQuery)
            doAnswer { call -> campsiteListener = call.getArgument(0); mock(ListenerRegistration::class.java) }.`when`(campsiteQuery).addSnapshotListener(any<EventListener<QuerySnapshot>>())
            doAnswer { call ->
                scheduleListener = call.getArgument(1)
                mock(ListenerRegistration::class.java).also(scheduleRegistrations::add)
            }.`when`(scheduleQuery).addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>())
            doAnswer { call -> lotteryListener = call.getArgument(0); mock(ListenerRegistration::class.java) }.`when`(lotteryQuery).addSnapshotListener(any<EventListener<QuerySnapshot>>())
            var canonical: CampsiteRepository? = null
            lotteries = LotteryRepository(db, campsiteLookup = { canonical?.campsitesMap?.get(it) })
            campsites = CampsiteRepository(db, sharedLotteryRepository = lotteries)
            canonical = campsites
        }

        fun emitCampsites(vararg ids: String) {
            val documents = ids.map { id ->
                val camp = Campsite(name = id, ownerID = "owner-$id", occupancy = 4)
                document(id, emptyMap()).also { `when`(it.toObject(Campsite::class.java)).thenReturn(camp) }
            }
            campsiteListener.onEvent(snapshot(documents), null)
        }

        fun emitLottery(waitlist: List<String>, winners: List<String> = emptyList(), drawn: Boolean = false, campsiteIds: List<String> = listOf("a", "b")) {
            val current = document("current", mapOf(
                "id" to "lot", "ownerId" to "owner-a", "createdById" to "owner-a", "campsiteIds" to campsiteIds,
                "waitlist" to waitlist, "winners" to winners, "drawn" to drawn, "isPrimary" to true,
                "numWinners" to 1L, "waitlistCapacity" to 5L,
                "registrationStart" to "2035-01-01T00:00", "registrationEnd" to "2035-01-02T00:00"))
            val reference = mock(DocumentReference::class.java)
            val lotteryCollection = mock(CollectionReference::class.java)
            val campsite = mock(DocumentReference::class.java)
            `when`(reference.parent).thenReturn(lotteryCollection)
            `when`(lotteryCollection.parent).thenReturn(campsite)
            `when`(campsite.id).thenReturn(campsiteIds.first())
            `when`(current.reference).thenReturn(reference)
            lotteryListener.onEvent(snapshot(listOf(current)), null)
        }

        fun emitNoLotteries() { lotteryListener.onEvent(snapshot(emptyList()), null) }

        fun emitSchedules(cached: Boolean = false) {
            scheduleListener.onEvent(snapshot(listOf(document("lot", mapOf(
                "lotteryId" to "lot", "ownerId" to "owner-a", "campsiteIds" to listOf("a", "b"),
                "bookingStart" to "2035-01-05T00:00", "bookingEnd" to "2035-01-10T00:00",
                "campsitePeriods" to emptyMap<String, Any>(), "bookings" to emptyList<Any>()))), cached), null)
        }

        fun failSchedules() = scheduleListener.onEvent(null, mock(FirebaseFirestoreException::class.java))

        fun latestScheduleListener() = scheduleListener

        fun assertOneListenerPerAttempt() {
            assertEquals(2, scheduleRegistrations.size)
            verify(scheduleRegistrations.first()).remove()
            verify(scheduleRegistrations.last(), never()).remove()
        }

        fun representations(vararg ids: String): List<Campsite> = ids.flatMap { id ->
            listOf(campsites.campsites.single { it.name == id }, campsites.campsitesMap.getValue(id))
        }

        private fun document(id: String, fields: Map<String, Any?>): DocumentSnapshot {
            val doc = mock(DocumentSnapshot::class.java)
            `when`(doc.id).thenReturn(id)
            `when`(doc.exists()).thenReturn(true)
            doAnswer { fields[it.getArgument<String>(0)] }.`when`(doc).get(anyString())
            doAnswer { fields[it.getArgument<String>(0)] as? String }.`when`(doc).getString(anyString())
            doAnswer { (fields[it.getArgument<String>(0)] as? Number)?.toLong() }.`when`(doc).getLong(anyString())
            doAnswer { fields[it.getArgument<String>(0)] as? Boolean }.`when`(doc).getBoolean(anyString())
            return doc
        }

        private fun snapshot(documents: List<DocumentSnapshot>, cached: Boolean = false): QuerySnapshot = mock(QuerySnapshot::class.java).also {
            val metadata = mock(SnapshotMetadata::class.java)
            `when`(metadata.isFromCache).thenReturn(cached)
            `when`(it.metadata).thenReturn(metadata)
            `when`(it.documents).thenReturn(documents)
        }

        override fun close() { campsites.close() }
    }
}
