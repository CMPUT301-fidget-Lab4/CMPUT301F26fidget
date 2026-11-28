package com.example.myapplication.lottery

import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.JoinWaitlistResult
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.LocalDateTime

/** Executes real membership logic; the fake only models external transaction attempts and commits. */
class WaitlistCommitContractTest {
    @Test fun joinEligibilityIncludesOpeningInstantButExcludesClosingInstant() {
        val start = LocalDateTime.of(2035, 1, 1, 0, 0)
        val lottery = com.example.myapplication.features.lottery.Lottery(id = "lot", ownerId = "owner",
            period = com.example.myapplication.features.lottery.Registration(start, start.plusDays(1)))
        assertFalse(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), start.minusNanos(1)))
        assertTrue(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), start))
        assertTrue(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), start.plusDays(1).minusNanos(1)))
        assertFalse(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), start.plusDays(1)))
    }

    @Test fun joinEligibilityCountsUnresolvedIdsAndZeroCapacityIsUnlimited() {
        val now = LocalDateTime.of(2035, 1, 1, 0, 0)
        val lottery = com.example.myapplication.features.lottery.Lottery(id = "lot", ownerId = "owner", waitlist = listOf("unresolved"), waitlistCapacity = 1,
            period = com.example.myapplication.features.lottery.Registration(now.minusDays(1), now.plusDays(1)))
        assertFalse(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), now))
        assertTrue(LotteryRepository.canJoinWaitlist(lottery.copy(waitlistCapacity = 2), "entrant", emptyList(), now))
        assertTrue(LotteryRepository.canJoinWaitlist(lottery.copy(waitlistCapacity = 0), "entrant", emptyList(), now))
    }

    @Test fun joinEligibilityRejectsWinnerDrawnLotteryAndInvalidEntrantIds() {
        val now = LocalDateTime.of(2035, 1, 1, 0, 0)
        val lottery = com.example.myapplication.features.lottery.Lottery(id = "lot", ownerId = "owner",
            period = com.example.myapplication.features.lottery.Registration(now.minusDays(1), now.plusDays(1)))
        assertTrue(LotteryRepository.canJoinWaitlist(lottery, "entrant", emptyList(), now))
        assertFalse(LotteryRepository.canJoinWaitlist(lottery.copy(winners = listOf("entrant")), "entrant", emptyList(), now))
        assertFalse(LotteryRepository.canJoinWaitlist(lottery.copy(drawn = true), "entrant", emptyList(), now))
        listOf("", " ", "bad/id").forEach { id -> assertFalse(LotteryRepository.canJoinWaitlist(lottery, id, emptyList(), now)) }
    }
    @Test fun successCallbackAfterProfileListenerRefreshDoesNotDuplicateLocalJoin() {
        val f = Fixture()
        f.join(); f.attempt()
        // The users listener can publish the committed profile before the Task callback.
        f.user.joinedLotteries = listOf(JoinedLottery("existing", "c"), JoinedLottery("lot", "b"))
        f.commit()
        assertEquals(true, f.result)
        assertEquals(listOf(JoinedLottery("existing", "c"), JoinedLottery("lot", "b")), f.user.joinedLotteries)
        assertEquals(listOf(f.existing, "legacy", f.membership), f.savedMembership())
        f.assertWaitlists(listOf("other", "entrant"))
    }
    @Test fun successfulJoinUpdatesBothCopiesAndSavedMembershipOnlyAfterCommit() {
        val f = Fixture()
        f.join()
        assertNull(f.result)
        assertEquals(listOf("existing"), f.user.joinedLotteries.map { it.lotteryId })
        f.attempt()
        assertNull(f.result)
        f.assertWaitlists(listOf("other"))
        assertEquals(listOf("existing"), f.user.joinedLotteries.map { it.lotteryId })
        f.commit()
        assertEquals(true, f.result)
        f.assertWaitlists(listOf("other", "entrant"))
        assertEquals(listOf(f.existing, "legacy", mapOf("lotteryId" to "lot", "campsiteName" to "b")), f.savedMembership())
        assertEquals(listOf(JoinedLottery("existing", "c"), JoinedLottery("lot", "b")), f.user.joinedLotteries)
    }

    @Test fun failedJoinCommitDoesNotPublishLocalOrSavedMembership() {
        val f = Fixture()
        f.join(); f.attempt(); f.fail()
        assertEquals(false, f.result)
        f.assertWaitlists(listOf("other"))
        assertEquals(listOf(f.existing, "legacy"), f.savedMembership())
        assertEquals(listOf(JoinedLottery("existing", "c")), f.user.joinedLotteries)
    }

    @Test fun retriedJoinPreservesConcurrentEntrantAndAddsActorExactlyOnce() {
        val f = Fixture()
        f.join(); f.attempt()
        f.copies.forEach { f.records.getValue(it)["waitlist"] = listOf("other", "concurrent") }
        f.records.getValue("users/entrant")["joinedLotteries"] = listOf(f.existing, "legacy", mapOf("lotteryId" to "concurrent", "campsiteName" to "d"))
        f.attempt(); f.commit()
        assertEquals(true, f.result)
        f.assertWaitlists(listOf("other", "concurrent", "entrant"))
        assertEquals(listOf(f.existing, "legacy", mapOf("lotteryId" to "concurrent", "campsiteName" to "d"), mapOf("lotteryId" to "lot", "campsiteName" to "b")), f.savedMembership())
        assertEquals(1, f.user.joinedLotteries.count { it.lotteryId == "lot" })
    }

    @Test fun repeatedJoinCannotDuplicateEitherMembership() {
        val f = Fixture()
        f.join(); f.attempt(); f.commit()
        f.join(); f.attempt(); f.commit()
        assertEquals(false, f.result)
        f.assertWaitlists(listOf("other", "entrant"))
        assertEquals(1, f.savedMembership().count { (it as? Map<*, *>)?.get("lotteryId") == "lot" })
        assertEquals(1, f.user.joinedLotteries.count { it.lotteryId == "lot" })
    }

    @Test fun joinRetryCannotEnrollInTheListingThatReplacedTheDisplayedOne() {
        val fixture = Fixture()
        fixture.join()
        fixture.attempt()
        fixture.copies.forEach { fixture.records.getValue(it)["id"] = "replacement" }
        val afterReplacement = fixture.records.mapValues { it.value.toMap() }

        fixture.attempt()
        fixture.commit()

        assertEquals(JoinWaitlistResult.LISTING_CHANGED, fixture.joinResult)
        assertEquals(afterReplacement, fixture.records)
        assertEquals(listOf(JoinedLottery("existing", "c")), fixture.user.joinedLotteries)
    }

    @Test fun joinRejectsWhenSavedProfileIsNoLongerEligible() {
        val fixture = Fixture()
        fixture.records.getValue("users/entrant")["userType"] = "campsite owner"
        fixture.join()
        fixture.attempt()
        fixture.commit()

        assertEquals(JoinWaitlistResult.INELIGIBLE, fixture.joinResult)
        fixture.assertWaitlists(listOf("other"))
        assertEquals(listOf(fixture.existing, "legacy"), fixture.savedMembership())
    }

    @Test fun successfulLeavePreservesUnrelatedDataAndPublishesOnlyAfterCommit() {
        val f = Fixture(member = true)
        f.leave(); f.attempt()
        assertNull(f.result)
        f.assertWaitlists(listOf("other", "entrant"))
        assertEquals(2, f.user.joinedLotteries.size)
        f.commit()
        assertEquals(true, f.result)
        f.assertWaitlists(listOf("other"))
        assertEquals(listOf(f.existing, "legacy"), f.savedMembership())
        assertEquals(listOf(JoinedLottery("existing", "c")), f.user.joinedLotteries)
    }

    @Test fun failedLeaveCommitRetainsBothCopiesSavedAndLocalMembership() {
        val f = Fixture(member = true)
        val before = f.records.mapValues { it.value.toMap() }
        val localBefore = f.user.joinedLotteries.toList()
        f.leave(); f.attempt(); f.fail()
        assertEquals(false, f.result)
        assertEquals(before, f.records)
        assertEquals(localBefore, f.user.joinedLotteries)
    }

    @Test fun retriedLeaveRechecksDrawAndDoesNotRemoveMembershipAfterDraw() {
        val f = Fixture(member = true)
        val before = f.user.joinedLotteries.toList()
        f.leave(); f.attempt()
        f.copies.forEach { f.records.getValue(it)["drawn"] = true }
        f.attempt(); f.commit()
        assertEquals(false, f.result)
        f.assertWaitlists(listOf("other", "entrant"))
        assertEquals(before, f.user.joinedLotteries)
        assertEquals(listOf(f.existing, "legacy", f.membership), f.savedMembership())
    }

    @Test fun inconsistentSharedCopyRejectsWithoutAnyWritesOrLocalChange() {
        val f = Fixture()
        f.records.getValue(f.copies.last())["id"] = "replacement"
        val before = f.records.mapValues { it.value.toMap() }
        f.join(); f.attempt(); f.commit()
        assertEquals(false, f.result)
        assertEquals(before, f.records)
        assertEquals(listOf(JoinedLottery("existing", "c")), f.user.joinedLotteries)
    }

    private class Fixture(member: Boolean = false) {
        val existing = mapOf("lotteryId" to "existing", "campsiteName" to "c")
        val membership = mapOf("lotteryId" to "lot", "campsiteName" to "b")
        val copies = listOf("campsites/a/lottery/current", "campsites/b/lottery/current")
        val records = linkedMapOf<String, MutableMap<String, Any?>>()
        val user = User(deviceID = "entrant", userType = "entrant", joinedLotteries =
            listOf(JoinedLottery("existing", "c")) + if (member) listOf(JoinedLottery("lot", "b")) else emptyList())
        var result: Boolean? = null
        var joinResult: JoinWaitlistResult? = null
        private val db = mock(FirebaseFirestore::class.java)
        private val refs = mutableMapOf<String, DocumentReference>()
        private lateinit var body: Transaction.Function<Any?>
        private var attemptResult: Any? = null
        private var attemptFailure: Exception? = null
        private var staged = mutableMapOf<String, Map<String, Any?>>()
        private lateinit var success: OnSuccessListener<Any?>
        private lateinit var failure: OnFailureListener
        @Suppress("UNCHECKED_CAST") private val task = mock(Task::class.java) as Task<Any?>
        private val repo: LotteryRepository

        init {
            val camps = mock(CollectionReference::class.java)
            val users = mock(CollectionReference::class.java)
            `when`(db.collection("campsites")).thenReturn(camps)
            `when`(db.collection("users")).thenReturn(users)
            listOf("a", "b").forEach { id ->
                val parent = ref("campsites/$id")
                val collection = mock(CollectionReference::class.java)
                `when`(camps.document(id)).thenReturn(parent)
                `when`(parent.collection("lottery")).thenReturn(collection)
                val copyRef = ref("campsites/$id/lottery/current")
                `when`(collection.document("current")).thenReturn(copyRef)
                records["campsites/$id"] = mutableMapOf("ownerID" to "owner", "occupancy" to 4L)
            }
            val userRef = ref("users/entrant")
            `when`(users.document("entrant")).thenReturn(userRef)
            copies.forEach { path -> records[path] = mutableMapOf(
                "id" to "lot", "ownerId" to "owner", "createdById" to "owner", "campsiteIds" to listOf("a", "b"),
                "waitlist" to if (member) listOf("other", "entrant") else listOf("other"), "winners" to emptyList<String>(),
                "drawn" to false, "waitlistCapacity" to 4L,
                "registrationStart" to LocalDateTime.now().minusDays(2).toString(),
                "registrationEnd" to LocalDateTime.now().plusDays(2).toString()) }
            records["users/entrant"] = mutableMapOf(
                "userType" to "entrant",
                "joinedLotteries" to (listOf(existing, "legacy") + if (member) listOf(membership) else emptyList())
            )
            doAnswer { call -> body = call.getArgument(0); task }.`when`(db).runTransaction<Any?>(any())
            doAnswer { call -> success = call.getArgument(0); task }.`when`(task).addOnSuccessListener(any())
            doAnswer { call -> failure = call.getArgument(0); task }.`when`(task).addOnFailureListener(any())
            repo = LotteryRepository(db)
        }

        private fun ref(path: String): DocumentReference = refs.getOrPut(path) {
            mock(DocumentReference::class.java).also { `when`(it.path).thenReturn(path) }
        }

        private fun snapshot(ref: DocumentReference): DocumentSnapshot {
            val data = records[ref.path]?.toMap()
            val doc = mock(DocumentSnapshot::class.java)
            `when`(doc.exists()).thenReturn(data != null)
            `when`(doc.reference).thenReturn(ref)
            doAnswer { data?.get(it.getArgument<String>(0)) }.`when`(doc).get(anyString())
            doAnswer { data?.get(it.getArgument<String>(0)) as? String }.`when`(doc).getString(anyString())
            doAnswer { (data?.get(it.getArgument<String>(0)) as? Number)?.toLong() }.`when`(doc).getLong(anyString())
            doAnswer { data?.get(it.getArgument<String>(0)) as? Boolean }.`when`(doc).getBoolean(anyString())
            return doc
        }

        fun join() {
            result = null
            joinResult = null
            repo.joinLotteryWaitlist("b", user, "lot") {
                joinResult = it
                result = it == JoinWaitlistResult.JOINED
            }
        }
        fun leave() { result = null; repo.leaveLotteryWaitlist("b", user, "lot") { result = it } }
        fun attempt() {
            staged = mutableMapOf(); attemptFailure = null; attemptResult = null
            val tx = mock(Transaction::class.java)
            doAnswer { snapshot(it.getArgument(0)) }.`when`(tx).get(any(DocumentReference::class.java))
            doAnswer { call ->
                val ref: DocumentReference = call.getArgument(0)
                val fields: Map<String, Any?> = call.getArgument(1)
                staged[ref.path] = (staged[ref.path] ?: records.getValue(ref.path)) + fields
                tx
            }.`when`(tx).update(any(DocumentReference::class.java), anyMap<String, Any>())
            doAnswer { call ->
                val ref: DocumentReference = call.getArgument(0)
                staged[ref.path] = (staged[ref.path] ?: records.getValue(ref.path)) + (call.getArgument<String>(1) to call.getArgument<Any?>(2))
                tx
            }.`when`(tx).update(any(DocumentReference::class.java), anyString(), any<Any>())
            try { attemptResult = body.apply(tx) } catch (e: Exception) { attemptFailure = e }
        }
        fun commit() {
            attemptFailure?.let { failure.onFailure(it); return }
            staged.forEach { (path, fields) -> records[path] = fields.toMutableMap() }
            success.onSuccess(attemptResult)
        }
        fun fail() { failure.onFailure(IllegalStateException("simulated commit failure")) }
        fun savedMembership(): List<*> = records.getValue("users/entrant").getValue("joinedLotteries") as List<*>
        fun assertWaitlists(expected: List<String>) { copies.forEach { assertEquals(expected, records.getValue(it)["waitlist"]) } }
    }
}
