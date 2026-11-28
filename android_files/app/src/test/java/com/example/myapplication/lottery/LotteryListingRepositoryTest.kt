package com.example.myapplication.lottery

import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.LocalDateTime

/** Executes the actual repository transaction body against fake document snapshots. */
class LotteryListingRepositoryTest {
    private val db = mock(FirebaseFirestore::class.java)
    private val tx = mock(Transaction::class.java)
    private val campCollection = mock(CollectionReference::class.java)
    private val scheduleCollection = mock(CollectionReference::class.java)
    private val copies = mutableMapOf<String, DocumentReference>()
    private val parents = mutableMapOf<String, DocumentSnapshot>()
    private val records = mutableMapOf<String, DocumentSnapshot>()
    private val schedule = mock(DocumentSnapshot::class.java)
    private val start = LocalDateTime.of(2035, 1, 1, 0, 0)
    private val owner = User(userType = "campsite owner", deviceID = "owner")
    private val repository: LotteryRepository

    init {
        `when`(db.collection("campsites")).thenReturn(campCollection)
        `when`(db.collection("bookingSchedules")).thenReturn(scheduleCollection)
        listOf("a", "b").forEach { id ->
            val parentRef = mock(DocumentReference::class.java)
            val subcollection = mock(CollectionReference::class.java)
            val copy = mock(DocumentReference::class.java)
            val parent = mock(DocumentSnapshot::class.java)
            val record = mock(DocumentSnapshot::class.java)
            copies[id] = copy; parents[id] = parent; records[id] = record
            `when`(campCollection.document(id)).thenReturn(parentRef)
            `when`(parentRef.collection("lottery")).thenReturn(subcollection)
            `when`(subcollection.document("current")).thenReturn(copy)
            `when`(tx.get(parentRef)).thenReturn(parent)
            `when`(tx.get(copy)).thenReturn(record)
            `when`(parent.exists()).thenReturn(true)
            `when`(parent.getString("ownerID")).thenReturn("owner")
            `when`(parent.getLong("occupancy")).thenReturn(4L)
            `when`(record.exists()).thenReturn(true)
            `when`(record.getString("id")).thenReturn("lot")
            `when`(record.getString("ownerId")).thenReturn("owner")
            `when`(record.getString("registrationStart")).thenReturn(start.toString())
            `when`(record.getString("registrationEnd")).thenReturn(start.plusDays(1).toString())
            `when`(record.get("campsiteIds")).thenReturn(listOf("a", "b"))
            `when`(record.get("waitlist")).thenReturn(listOf("one", "two"))
            `when`(record.getLong("numWinners")).thenReturn(1L)
        }
        val scheduleRef = mock(DocumentReference::class.java)
        `when`(scheduleCollection.document("lot")).thenReturn(scheduleRef)
        `when`(tx.get(scheduleRef)).thenReturn(schedule)
        `when`(schedule.exists()).thenReturn(true)
        `when`(schedule.id).thenReturn("lot")
        `when`(schedule.getString("lotteryId")).thenReturn("lot")
        `when`(schedule.get("campsiteIds")).thenReturn(listOf("a", "b"))
        `when`(schedule.getString("bookingStart")).thenReturn(start.plusDays(5).toString())
        `when`(schedule.getString("bookingEnd")).thenReturn(start.plusDays(8).toString())
        doAnswer { invocation ->
            invocation.getArgument<Transaction.Function<Void>>(0).apply(tx)
            @Suppress("UNCHECKED_CAST")
            (mock(Task::class.java) as Task<Void>)
        }.`when`(db).runTransaction<Void>(any())
        repository = LotteryRepository(db)
    }

    private fun save(user: User = owner, capacity: Int = 5, id: String = "lot") = repository.updateLottery(
        user, "a", Registration(start, start.plusDays(2)), 2, capacity, expectedLotteryId = id)

    private fun assertNoWrites() {
        copies.values.forEach { verify(tx, never()).update(eq(it), anyMap<String, Any>()) }
    }

    @Test fun updatesExistingPrimaryAndSecondaryWithoutOverwritingMembership() {
        save()
        copies.values.forEach { ref ->
            @Suppress("UNCHECKED_CAST")
            val fields = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
            @Suppress("UNCHECKED_CAST")
            verify(tx).update(eq(ref), fields.capture())
            assertEquals(2, fields.value["numWinners"])
            assertEquals(5, fields.value["waitlistCapacity"])
            assertEquals(start.plusDays(2).toString(), fields.value["registrationEnd"])
            assertFalse(fields.value.containsKey("waitlist"))
            assertFalse(fields.value.containsKey("winners"))
            assertFalse(fields.value.containsKey("id"))
        }
    }

    @Test fun anotherOwnerCannotSaveAnyCopy() {
        assertThrows(IllegalStateException::class.java) { save(owner.copy(deviceID = "other")) }
        assertNoWrites()
    }

    @Test fun matchingDeviceIdWithEntrantRoleStillCannotSave() {
        assertThrows(IllegalStateException::class.java) { save(owner.copy(userType = "entrant")) }
        assertNoWrites()
    }

    @Test fun changedSecondaryOwnershipBlocksSaveButAdminCanSave() {
        `when`(parents.getValue("b").getString("ownerID")).thenReturn("other")
        assertThrows(IllegalStateException::class.java) { save() }
        assertNoWrites()
        save(User(userType = "admin", deviceID = "admin"))
        copies.values.forEach { verify(tx).update(eq(it), anyMap<String, Any>()) }
    }

    @Test fun latestMembershipAndOccupancyAreValidatedBeforeWriting() {
        assertThrows(IllegalStateException::class.java) { save(capacity = 1) }
        assertNoWrites()
        `when`(parents.getValue("b").getLong("occupancy")).thenReturn(1L)
        assertThrows(IllegalStateException::class.java) { save() }
        assertNoWrites()
    }

    @Test fun replacedOrDrawnLotteryCannotBeEditedThroughStaleForm() {
        assertThrows(IllegalStateException::class.java) { save(id = "old-lottery") }
        assertNoWrites()
        `when`(records.getValue("a").getBoolean("drawn")).thenReturn(true)
        assertThrows(IllegalStateException::class.java) { save() }
        assertNoWrites()
    }

    @Test fun laterCampsiteOverridesDoNotAllowRegistrationPastDefaultBookingStart() {
        val defaultStart = start.plusDays(1)
        val overrideStart = start.plusDays(4)
        `when`(schedule.getString("bookingStart")).thenReturn(defaultStart.toString())
        `when`(schedule.get("campsitePeriods")).thenReturn(listOf("a", "b").associateWith {
            mapOf("start" to overrideStart.toString(), "end" to overrideStart.plusDays(2).toString())
        })

        val error = assertThrows(IllegalStateException::class.java) { save() }
        assertEquals("Registration must close by the time booking starts.", error.message)
        assertNoWrites()
    }

    @Test fun invalidScheduleDoesNotSaveListing() {
        `when`(schedule.exists()).thenReturn(false)
        assertThrows(IllegalStateException::class.java) { save() }
        assertNoWrites()
    }

    @Test fun invalidRawInputNeverStartsAWriteTransaction() {
        val lottery = com.example.myapplication.features.lottery.Lottery(id = "lot", campsiteIds = listOf("a", "b"))
        val sites = listOf("a", "b").associateWith { com.example.myapplication.features.campsite.Campsite(ownerID = "owner", occupancy = 4) }
        val schedule = com.example.myapplication.features.booking.BookingSchedule(lottery, start.plusDays(5), start.plusDays(8))
        val result = repository.updateLotteryFromInput(owner, lottery, sites, start.toString(), start.plusDays(2).toString(), "", "5", schedule)
        assertFalse(result.isSuccessful)
        assertTrue(result.exception is IllegalArgumentException)
        verify(db, never()).runTransaction<Void>(any())
        assertNoWrites()
    }

    @Test fun validRawInputIsParsedAndSavedByRepository() {
        val lottery = com.example.myapplication.features.lottery.Lottery(id = "lot", campsiteIds = listOf("a", "b"))
        val sites = listOf("a", "b").associateWith { com.example.myapplication.features.campsite.Campsite(ownerID = "owner", occupancy = 4) }
        val schedule = com.example.myapplication.features.booking.BookingSchedule(lottery, start.plusDays(5), start.plusDays(8))
        repository.updateLotteryFromInput(owner, lottery, sites, start.toString(), start.plusDays(2).toString(), "2", "5", schedule)
        copies.values.forEach { ref ->
            @Suppress("UNCHECKED_CAST")
            val fields = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
            verify(tx).update(eq(ref), fields.capture())
            assertEquals(start.toString(), fields.value["registrationStart"])
            assertEquals(start.plusDays(2).toString(), fields.value["registrationEnd"])
            assertEquals(2, fields.value["numWinners"])
            assertEquals(5, fields.value["waitlistCapacity"])
            assertFalse(fields.value.containsKey("waitlist"))
            assertFalse(fields.value.containsKey("winners"))
        }
    }
    @Test fun creationPersistsActualCreatorAndCampsiteOwnerOnEveryCopy() {
        records.values.forEach { `when`(it.exists()).thenReturn(false) }
        `when`(schedule.exists()).thenReturn(false)
        val query = mock(Query::class.java)
        `when`(scheduleCollection.whereArrayContainsAny("campsiteIds", listOf("a", "b"))).thenReturn(query)
        @Suppress("UNCHECKED_CAST")
        val read = mock(Task::class.java) as Task<QuerySnapshot>
        @Suppress("UNCHECKED_CAST")
        val aggregate = mock(Task::class.java) as Task<List<QuerySnapshot>>
        `when`(query.get(Source.SERVER)).thenReturn(read)
        val empty = mock(QuerySnapshot::class.java)
        `when`(empty.documents).thenReturn(emptyList())
        mockStatic(com.google.android.gms.tasks.Tasks::class.java).use { tasks ->
            tasks.`when`<Task<List<QuerySnapshot>>> {
                com.google.android.gms.tasks.Tasks.whenAllSuccess<QuerySnapshot>(listOf(read))
            }.thenReturn(aggregate)
            tasks.`when`<List<QuerySnapshot>> {
                com.google.android.gms.tasks.Tasks.await(aggregate, 15, java.util.concurrent.TimeUnit.SECONDS)
            }.thenReturn(listOf(empty))
            repository.createLottery(User(deviceID = "admin-creator", userType = "admin"), "lot", listOf("a", "b"),
                Registration(start, start.plusDays(2)), 2, 5, start.plusDays(5), start.plusDays(8))
        }
        copies.forEach { (id, ref) ->
            @Suppress("UNCHECKED_CAST")
            val fields = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, Any>>
            verify(tx).set(eq(ref), fields.capture())
            assertEquals("admin-creator", fields.value["createdById"])
            assertEquals("owner", fields.value["ownerId"])
            assertEquals("lot", fields.value["id"])
            assertEquals(listOf("a", "b"), fields.value["campsiteIds"])
            assertEquals(id == "a", fields.value["isPrimary"])
        }
    }
}
