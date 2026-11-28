package com.example.myapplication.lottery

import com.example.myapplication.features.campsite.CampsiteLocation
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.data.firebase.CampsiteRepository
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.LotteryDrawOutcome
import com.example.myapplication.core.datetime.AlbertaTime
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class LotteryDrawRepositoryTest {
    private lateinit var taskMethods: MockedStatic<Tasks>

    @Before
    fun replaceMainLooperTaskAggregation() {
        taskMethods = mockStatic(Tasks::class.java)
        taskMethods.`when`<Task<Any?>> { Tasks.forResult(any<Any?>()) }.thenCallRealMethod()
        // Play Services initializes its Android main-thread executor even with an explicit executor.
        taskMethods.`when`<Task<List<Task<*>>>> {
            Tasks.whenAllComplete(any(Executor::class.java), any<Collection<Task<*>>>())
        }.thenAnswer { call ->
            val executor = call.getArgument<Executor>(0)
            val inputs = call.getArgument<Collection<Task<*>>>(1).toList()
            val complete = TaskCompletionSource<List<Task<*>>>()
            inputs.forEach { task ->
                task.addOnCompleteListener(executor) {
                    if (inputs.all { it.isComplete }) complete.trySetResult(inputs)
                }
            }
            if (inputs.isEmpty()) complete.setResult(emptyList())
            complete.task
        }
    }

    @After
    fun restorePlayServicesTasks() {
        taskMethods.close()
    }

    @Test
    fun registrationClosingInstantCommitsTheSharedResultAndInvitationTogether() {
        val fixture = Fixture()
        val task = fixture.draw()
        val draw = fixture.pending.single()
        val before = fixture.saved.toMap()

        draw.attempt()

        assertEquals(before, fixture.saved)
        assertFalse(task.isComplete)

        draw.commit()

        assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), task.result)
        val primary = fixture.lottery("first")
        val winners = primary["winners"] as List<*>
        assertEquals(1, winners.size)
        val winner = winners.single() as String
        assertTrue(winner in setOf("alice", "bonnie", "chris"))
        for (campsite in setOf("first", "second")) {
            assertEquals(winners, fixture.lottery(campsite)["winners"])
            assertEquals(true, fixture.lottery(campsite)["drawn"])
            assertEquals(false, fixture.campsite(campsite)["inLottery"])
        }

        val invitation = fixture.saved.getValue("reservations/lottery_$winner") as Reservation
        assertEquals("lottery_$winner", invitation.id)
        assertEquals("lottery", invitation.lotteryId)
        assertEquals("current-owner", invitation.ownerId)
        assertEquals(winner, invitation.entrantId)
        assertEquals(ReservationStatus.INVITED, invitation.status)
        assertEquals(setOf("reservations/lottery_$winner"), fixture.saved.keys.filter {
            it.startsWith("reservations/")
        }.toSet())

        val result = fixture.saved.getValue("lotteryDrawResults/lottery") as Map<*, *>
        assertEquals("first", result["campsiteName"])
        assertEquals(winners, result["winnerIds"])
        assertEquals(listOf("alice", "bonnie", "chris"), result["remainingEntrantIds"])
        assertTrue(fixture.saved.keys.none { it.startsWith("notifications/") })
    }

    @Test
    fun beforeClosingInstantAndWithOnlyHistoricalCopiesNoDrawRuns() {
        val open = Fixture(now = Fixture.registrationEnd.minusNanos(1))
        assertEquals(emptyList<LotteryDrawOutcome>(), open.draw().result)
        assertTrue(open.pending.isEmpty())

        val archived = Fixture(publishCurrent = false)
        assertEquals(emptyList<LotteryDrawOutcome>(), archived.draw().result)
        assertTrue(archived.pending.isEmpty())
    }

    @Test
    fun emptyWaitlistStillClosesTheLotteryWithoutInvitationsOrResultDelivery() {
        val fixture = Fixture(waitlist = emptyList())
        val task = fixture.draw()
        fixture.pending.single().apply { attempt(); commit() }

        assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), task.result)
        for (campsite in setOf("first", "second")) {
            assertEquals(emptyList<String>(), fixture.lottery(campsite)["winners"])
            assertEquals(true, fixture.lottery(campsite)["drawn"])
            assertEquals(false, fixture.campsite(campsite)["inLottery"])
        }
        assertTrue(fixture.saved.keys.none {
            it.startsWith("reservations/") || it.startsWith("lotteryDrawResults/")
        })
    }

    @Test
    fun alreadyDrawnCopiesOnlyRepairStaleFlagsAndLeaveExistingDeliveryAlone() {
        val fixture = Fixture(drawn = true)
        val existingResult = mapOf("winnerIds" to listOf("alice"), "processed" to true)
        fixture.saved["lotteryDrawResults/lottery"] = existingResult
        val previousLottery = fixture.lottery("first")

        val task = fixture.draw()
        fixture.pending.single().apply { attempt(); commit() }

        assertEquals(listOf(LotteryDrawOutcome.Repaired("lottery")), task.result)
        assertEquals(previousLottery, fixture.lottery("first"))
        assertEquals(false, fixture.campsite("first")["inLottery"])
        assertEquals(false, fixture.campsite("second")["inLottery"])
        assertEquals(existingResult, fixture.saved["lotteryDrawResults/lottery"])
        assertTrue(fixture.saved.keys.none { it.startsWith("reservations/") })
    }

    @Test
    fun transactionRetryAfterAnotherDeviceDrawsDoesNotOverwriteTheCommittedResult() {
        val fixture = Fixture()
        val task = fixture.draw()
        val draw = fixture.pending.single()
        draw.attempt()

        fixture.replaceLottery("first", "drawn" to true, "winners" to listOf("chris"))
        fixture.replaceLottery("second", "drawn" to true, "winners" to listOf("chris"))
        fixture.saved["lotteryDrawResults/lottery"] = mapOf("winnerIds" to listOf("chris"))
        val publishedResult = fixture.saved.getValue("lotteryDrawResults/lottery")
        draw.attempt()
        draw.commit()

        assertEquals(listOf(LotteryDrawOutcome.Repaired("lottery")), task.result)
        assertEquals(listOf("chris"), fixture.lottery("first")["winners"])
        assertEquals(listOf("chris"), fixture.lottery("second")["winners"])
        assertEquals(publishedResult, fixture.saved["lotteryDrawResults/lottery"])
        assertTrue(fixture.saved.keys.none { it.startsWith("reservations/") })
    }

    @Test
    fun staleSnapshotAndInconsistentCurrentCopiesCannotModifyLiveListings() {
        val replacement = Fixture()
        for (campsite in setOf("first", "second")) {
            replacement.saved["campsites/$campsite/lottery/history_lottery"] =
                replacement.lottery(campsite)
            replacement.replaceLottery(campsite, "id" to "replacement")
        }
        val replacementState = replacement.saved.toMap()
        val replaced = replacement.draw()
        replacement.pending.single().apply { attempt(); commit() }
        assertEquals(listOf(LotteryDrawOutcome.Skipped("lottery")), replaced.result)
        assertEquals(replacementState, replacement.saved)

        val inconsistent = Fixture()
        inconsistent.replaceLottery("second", "id" to "other-lottery")
        val inconsistentState = inconsistent.saved.toMap()
        val rejected = inconsistent.draw()
        inconsistent.pending.single().apply { attempt(); commit() }
        assertEquals(listOf(LotteryDrawOutcome.Skipped("lottery")), rejected.result)
        assertEquals(inconsistentState, inconsistent.saved)
    }

    @Test
    fun failedCommitLeavesEveryDocumentUnchangedAndNextCheckCanDraw() {
        val fixture = Fixture()
        val before = fixture.saved.toMap()
        val failed = fixture.draw()
        fixture.pending.single().apply {
            attempt()
            fail()
        }

        val failure = failed.result.single() as LotteryDrawOutcome.Failed
        assertEquals("lottery", failure.lotteryId)
        assertEquals("Commit rejected", failure.error.message)
        assertEquals(before, fixture.saved)

        val retried = fixture.draw()
        fixture.pending.last().apply { attempt(); commit() }
        assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), retried.result)
        assertEquals(true, fixture.lottery("first")["drawn"])
    }

    @Test
    fun aMultiWinnerDrawInvitesExactlyTwoDistinctEligibleEntrants() {
        val fixture = Fixture(numWinners = 2)
        val task = fixture.draw()
        fixture.pending.single().apply { attempt(); commit() }

        assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), task.result)
        assertInvitations(fixture, expectedWinners = 2)
    }

    @Test
    fun aRetriedDrawCannotOverbookAfterCapacityDrops() {
        val fixture = Fixture(numWinners = 2)
        val task = fixture.draw()
        val pending = fixture.pending.single()
        pending.attempt()

        // A legacy or out-of-band write reduced one participating campsite to one place.
        fixture.saved["campsites/second"] = fixture.campsite("second") + ("occupancy" to 1L)
        pending.attempt()
        pending.commit()

        assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), task.result)
        assertInvitations(fixture, expectedWinners = 1)
    }

    @Test
    fun reducingCapacityCannotInvalidateAnUndrawnLottery() {
        val fixture = Fixture(numWinners = 2)
        val before = fixture.saved.toMap()

        val edit = fixture.editCapacity(1)
        fixture.pending.single().apply { attempt(); commit() }

        assertFalse(edit.isSuccessful)
        assertTrue(edit.exception?.message.orEmpty().contains("undrawn lottery"))
        assertEquals(before, fixture.saved)
    }

    @Test
    fun archivedWinnersKeepCapacityUntilTheirStayWindowEnds() {
        for ((windowEnd, mayReduce) in listOf(
            Fixture.registrationEnd.plusDays(2) to false,
            Fixture.registrationEnd.minusDays(1) to true
        )) {
            val fixture = Fixture(numWinners = 2)
            val draw = fixture.draw()
            fixture.pending.single().apply { attempt(); commit() }
            assertEquals(listOf(LotteryDrawOutcome.Committed("lottery")), draw.result)
            fixture.archiveDrawAndCreateNewListing()
            val scheduleLookup = fixture.stubBookingScheduleLookup(windowEnd)
            taskMethods.`when`<QuerySnapshot> { Tasks.await(scheduleLookup, 15, TimeUnit.SECONDS) }
                .thenReturn(scheduleLookup.result)

            val edit = fixture.editCapacity(1)
            fixture.pending.last().apply { attempt(); commit() }

            assertEquals(mayReduce, edit.isSuccessful)
            assertEquals(if (mayReduce) 1L else 2L, (fixture.campsite("first")["occupancy"] as Number).toLong())
            if (!mayReduce) assertTrue(edit.exception?.message.orEmpty().contains("stay window"))
        }
    }

    @Test
    fun nextDrawDelayUsesTheCurrentDeadlineAndIgnoresArchivedCopies() {
        assertEquals(90_000L, Fixture(now = Fixture.registrationEnd.minusSeconds(90)).nextDrawDelay())
        assertEquals(0L, Fixture().nextDrawDelay())
        assertEquals(null, Fixture(publishCurrent = false).nextDrawDelay())
    }

    private fun assertInvitations(fixture: Fixture, expectedWinners: Int) {
        val eligible = setOf("alice", "bonnie", "chris")
        val winners = (fixture.lottery("first")["winners"] as List<*>).filterIsInstance<String>()
        assertEquals(expectedWinners, winners.size)
        assertEquals(expectedWinners, winners.toSet().size)
        assertTrue(eligible.containsAll(winners))
        assertEquals(winners, fixture.lottery("second")["winners"])
        val invitations = fixture.saved.filterKeys { it.startsWith("reservations/") }
        assertEquals(winners.map { "reservations/lottery_$it" }.toSet(), invitations.keys)
        assertEquals(winners.toSet(), invitations.values.map { (it as Reservation).entrantId }.toSet())
        val result = fixture.saved.getValue("lotteryDrawResults/lottery") as Map<*, *>
        assertEquals(winners, result["winnerIds"])
        assertEquals(eligible, (result["remainingEntrantIds"] as List<*>).toSet())
    }

    private class Fixture(
        now: LocalDateTime = registrationEnd,
        waitlist: List<String> = listOf("alice", "bonnie", "chris", "alice", "invalid/id", " "),
        drawn: Boolean = false,
        publishCurrent: Boolean = true,
        numWinners: Int = 1
    ) {
        val saved = linkedMapOf<String, Any>()
        val pending = mutableListOf<PendingDraw>()
        private val db = mock(FirebaseFirestore::class.java)
        private val documents = mutableMapOf<String, DocumentReference>()
        private val collections = mutableMapOf<String, CollectionReference>()
        private val repo: LotteryRepository

        init {
            doAnswer { call -> collection(call.getArgument(0)) }.`when`(db).collection(anyString())
            repo = LotteryRepository(
                db = db,
                clock = Clock.fixed(now.atZone(AlbertaTime.zone).toInstant(), AlbertaTime.zone)
            )

            val query = mock(Query::class.java)
            var listener: EventListener<QuerySnapshot>? = null
            `when`(db.collectionGroup("lottery")).thenReturn(query)
            doAnswer { call ->
                listener = call.getArgument(0)
                mock(ListenerRegistration::class.java)
            }.`when`(query).addSnapshotListener(any<EventListener<QuerySnapshot>>())
            repo.startListening()

            for (campsite in listOf("first", "second")) {
                val fields = mapOf(
                    "id" to "lottery",
                    "ownerId" to "previous-owner",
                    "campsiteIds" to listOf("first", "second"),
                    "waitlist" to waitlist,
                    "winners" to if (drawn) listOf("alice") else emptyList(),
                    "drawn" to drawn,
                    "isPrimary" to (campsite == "first"),
                    "numWinners" to numWinners.toLong(),
                    "registrationStart" to registrationEnd.minusDays(2).toString(),
                    "registrationEnd" to registrationEnd.toString()
                )
                saved["campsites/$campsite"] =
                    mapOf("inLottery" to true, "ownerID" to "current-owner", "occupancy" to 2L)
                val document = if (publishCurrent) "current" else "history_lottery"
                saved["campsites/$campsite/lottery/$document"] = fields
            }
            val snapshot = mock(QuerySnapshot::class.java)
            val documentPaths = saved.keys.filter { "/lottery/" in it }.reversed()
            val snapshots = documentPaths.map { snapshot(document(it)) }
            val metadata = serverMetadata()
            `when`(snapshot.documents).thenReturn(snapshots)
            `when`(snapshot.metadata).thenReturn(metadata)
            requireNotNull(listener).onEvent(snapshot, null)

            doAnswer { call ->
                PendingDraw(call.getArgument(0)).also(pending::add).task
            }.`when`(db).runTransaction<Any?>(any())
        }

        fun draw(): Task<List<LotteryDrawOutcome>> = repo.drawClosedLotteries(setOf("first", "second"))
        fun nextDrawDelay(): Long? = repo.timeUntilNextDrawMillis(setOf("first", "second"))

        fun editCapacity(occupancy: Int): Task<Void> {
            val listener = mock(ListenerRegistration::class.java)
            `when`(collection("campsites").addSnapshotListener(any<EventListener<QuerySnapshot>>()))
                .thenReturn(listener)
            `when`(collection("bookingSchedules").addSnapshotListener(
                eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>()
            )).thenReturn(listener)
            return CampsiteRepository(db, sharedLotteryRepository = repo)
                .updateCampsiteDetails("first", CampsiteLocation(), "Updated", occupancy, "")
        }

        fun archiveDrawAndCreateNewListing() {
            for (campsite in listOf("first", "second")) {
                saved["campsites/$campsite/lottery/history_lottery"] = lottery(campsite)
                replaceLottery(campsite, "id" to "new-listing", "drawn" to false, "numWinners" to 1L)
            }
        }

        fun stubBookingScheduleLookup(windowEnd: LocalDateTime): Task<QuerySnapshot> {
            val path = "bookingSchedules/lottery"
            saved[path] = mapOf(
                "lotteryId" to "lottery",
                "campsiteIds" to listOf("first", "second"),
                "bookingStart" to windowEnd.minusDays(1).toString(),
                "bookingEnd" to windowEnd.toString(),
                "bookings" to emptyList<Any>()
            )
            val record = snapshot(document(path))
            val records = mock(QuerySnapshot::class.java)
            `when`(records.documents).thenReturn(listOf(record))
            val query = mock(Query::class.java)
            `when`(collection("bookingSchedules").whereArrayContains("campsiteIds", "first")).thenReturn(query)
            val task = TaskCompletionSource<QuerySnapshot>().apply { setResult(records) }.task
            `when`(query.get(Source.SERVER)).thenReturn(task)
            return task
        }

        @Suppress("UNCHECKED_CAST")
        fun lottery(campsite: String) =
            saved.getValue("campsites/$campsite/lottery/current") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun campsite(campsite: String) = saved.getValue("campsites/$campsite") as Map<String, Any?>

        fun replaceLottery(campsite: String, vararg changes: Pair<String, Any>) {
            saved["campsites/$campsite/lottery/current"] = lottery(campsite) + changes
        }

        private fun collection(path: String): CollectionReference = collections.getOrPut(path) {
            mock(CollectionReference::class.java).also { ref ->
                doAnswer { call -> document("$path/${call.getArgument<String>(0)}") }
                    .`when`(ref).document(anyString())
                if ('/' in path) {
                    val parent = document(path.substringBeforeLast('/'))
                    `when`(ref.parent).thenReturn(parent)
                }
            }
        }

        private fun document(path: String): DocumentReference = documents.getOrPut(path) {
            mock(DocumentReference::class.java).also { ref ->
                `when`(ref.id).thenReturn(path.substringAfterLast('/'))
                `when`(ref.path).thenReturn(path)
                val parent = collection(path.substringBeforeLast('/'))
                `when`(ref.parent).thenReturn(parent)
                doAnswer { call -> collection("$path/${call.getArgument<String>(0)}") }
                    .`when`(ref).collection(anyString())
            }
        }

        private fun snapshot(ref: DocumentReference): DocumentSnapshot {
            val id = ref.id
            val fields = saved[ref.path] as? Map<*, *>
            val metadata = serverMetadata()
            return mock(DocumentSnapshot::class.java).also { doc ->
                `when`(doc.id).thenReturn(id)
                `when`(doc.reference).thenReturn(ref)
                `when`(doc.exists()).thenReturn(fields != null)
                `when`(doc.metadata).thenReturn(metadata)
                doAnswer { fields?.get(it.getArgument<String>(0)) }.`when`(doc).get(anyString())
                doAnswer { fields?.get(it.getArgument<String>(0)) as? String }.`when`(doc).getString(anyString())
                doAnswer { fields?.get(it.getArgument<String>(0)) as? Boolean }.`when`(doc).getBoolean(anyString())
                doAnswer { (fields?.get(it.getArgument<String>(0)) as? Number)?.toLong() }.`when`(doc).getLong(anyString())
            }
        }

        private fun serverMetadata() = mock(SnapshotMetadata::class.java)

        inner class PendingDraw(private val draw: Transaction.Function<Any?>) {
            private val completion = TaskCompletionSource<Any?>()
            val task: Task<Any?> = completion.task
            private val writes = linkedMapOf<String, Any>()
            private var attemptedResult: Any? = null
            private var attemptedFailure: Exception? = null

            fun attempt() {
                writes.clear()
                val tx = mock(Transaction::class.java)
                doAnswer { call ->
                    check(writes.isEmpty()) { "Firestore requires every read before the first write." }
                    snapshot(call.getArgument(0))
                }.`when`(tx).get(any(DocumentReference::class.java))
                doAnswer { call ->
                    val ref = call.getArgument<DocumentReference>(0)
                    @Suppress("UNCHECKED_CAST")
                    val fields = (writes[ref.path] ?: saved.getValue(ref.path)) as Map<String, Any?>
                    writes[ref.path] = fields + call.getArgument<Map<String, Any>>(1)
                    tx
                }.`when`(tx).update(any(DocumentReference::class.java), anyMap<String, Any>())
                doAnswer { call ->
                    val ref = call.getArgument<DocumentReference>(0)
                    @Suppress("UNCHECKED_CAST")
                    val fields = (writes[ref.path] ?: saved.getValue(ref.path)) as Map<String, Any?>
                    writes[ref.path] = fields + (call.getArgument<String>(1) to call.getArgument<Any>(2))
                    tx
                }.`when`(tx).update(any(DocumentReference::class.java), anyString(), any<Any>())
                doAnswer { call ->
                    writes[call.getArgument<DocumentReference>(0).path] = call.getArgument(1)
                    tx
                }.`when`(tx).set(any(DocumentReference::class.java), any(Any::class.java))

                attemptedResult = null
                attemptedFailure = null
                try {
                    attemptedResult = draw.apply(tx)
                } catch (exception: Exception) {
                    attemptedFailure = exception
                }
            }

            fun commit() {
                attemptedFailure?.let { completion.setException(it); return }
                saved.putAll(writes)
                completion.setResult(attemptedResult)
            }

            fun fail() = completion.setException(IllegalStateException("Commit rejected"))
        }

        companion object {
            val registrationEnd: LocalDateTime = LocalDateTime.of(2035, 8, 1, 12, 0)
        }
    }
}
