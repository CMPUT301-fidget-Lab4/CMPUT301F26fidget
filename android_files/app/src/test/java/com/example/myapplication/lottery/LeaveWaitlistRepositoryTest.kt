package com.example.myapplication.lottery

import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.features.profile.User
import com.example.myapplication.features.lottery.JoinWaitlistResult
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canLeaveLotteryWaitlist
import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.LocalDateTime

/** Runs the leave transaction against fake snapshots; no Firebase connection is required. */
class LeaveWaitlistRepositoryTest {
    private val db = mock(FirebaseFirestore::class.java)
    private val tx = mock(Transaction::class.java)
    private val campsites = mock(CollectionReference::class.java)
    private val users = mock(CollectionReference::class.java)
    private val userRef = mock(DocumentReference::class.java)
    private val userSnapshot = mock(DocumentSnapshot::class.java)
    private val copies = mutableMapOf<String, DocumentReference>()
    private val snapshots = mutableMapOf<String, DocumentSnapshot>()
    private val parents = mutableMapOf<String, DocumentSnapshot>()
    private val user = User(userType = "entrant", deviceID = "entrant",
        joinedLotteries = listOf(JoinedLottery("lot", "a"), JoinedLottery("other", "c")))
    private var committedResult: Any? = null
    private var failure: Exception? = null
    @Suppress("UNCHECKED_CAST") private val task = mock(Task::class.java) as Task<Any?>
    private val repository: LotteryRepository

    init {
        `when`(db.collection("campsites")).thenReturn(campsites)
        `when`(db.collection("users")).thenReturn(users)
        `when`(users.document("entrant")).thenReturn(userRef)
        `when`(tx.get(userRef)).thenReturn(userSnapshot)
        `when`(userSnapshot.exists()).thenReturn(true)
        `when`(userSnapshot.getString("userType")).thenReturn("entrant")
        `when`(userSnapshot.get("joinedLotteries")).thenReturn(listOf(
            mapOf("lotteryId" to "lot", "campsiteName" to "a"),
            mapOf("lotteryId" to "other", "campsiteName" to "c"), "legacy-entry"))
        listOf("a", "b").forEach { id ->
            val parent = mock(DocumentReference::class.java)
            val lotteryCollection = mock(CollectionReference::class.java)
            val ref = mock(DocumentReference::class.java)
            val snapshot = mock(DocumentSnapshot::class.java)
            val parentSnapshot = mock(DocumentSnapshot::class.java)
            parents[id] = parentSnapshot
            `when`(tx.get(parent)).thenReturn(parentSnapshot)
            `when`(parentSnapshot.exists()).thenReturn(true)
            `when`(parentSnapshot.getString("ownerID")).thenReturn("owner")
            copies[id] = ref; snapshots[id] = snapshot
            `when`(campsites.document(id)).thenReturn(parent)
            `when`(parent.collection("lottery")).thenReturn(lotteryCollection)
            `when`(lotteryCollection.document("current")).thenReturn(ref)
            `when`(tx.get(ref)).thenReturn(snapshot)
            `when`(snapshot.exists()).thenReturn(true)
            `when`(snapshot.getString("id")).thenReturn("lot")
            `when`(snapshot.getString("registrationStart")).thenReturn("2035-01-01T00:00")
            `when`(snapshot.getString("registrationEnd")).thenReturn("2035-01-02T00:00")
            `when`(snapshot.get("campsiteIds")).thenReturn(listOf("a", "b"))
            `when`(snapshot.get("waitlist")).thenReturn(listOf("entrant", "other"))
        }
        doAnswer { call ->
            try { committedResult = call.getArgument<Transaction.Function<Any?>>(0).apply(tx) }
            catch (exception: Exception) { failure = exception }
            task
        }.`when`(db).runTransaction<Any?>(any())
        doAnswer { call ->
            if (failure == null) call.getArgument<OnSuccessListener<Any?>>(0).onSuccess(committedResult)
            task
        }.`when`(task).addOnSuccessListener(any())
        doAnswer { call ->
            failure?.let { call.getArgument<OnFailureListener>(0).onFailure(it) }
            task
        }.`when`(task).addOnFailureListener(any())
        repository = LotteryRepository(db)
    }

    private fun leave(expectedId: String = "lot"): Boolean? {
        var result: Boolean? = null
        repository.leaveLotteryWaitlist("b", user, expectedLotteryId = expectedId) { result = it }
        return result
    }

    private fun assertNoWrites() {
        copies.values.forEach { verify(tx, never()).update(eq(it), anyMap<String, Any>()) }
        verify(tx, never()).update(eq(userRef), eq("joinedLotteries"), any<Any>())
    }

    @Test fun leavingThroughSecondaryRemovesEntrantFromEveryCopyAndSavedMembership() {
        assertEquals(true, leave())
        copies.values.forEach { verify(tx).update(it, mapOf("waitlist" to listOf("other"))) }
        verify(tx).update(userRef, "joinedLotteries", listOf(mapOf("lotteryId" to "other", "campsiteName" to "c"), "legacy-entry"))
        assertEquals(listOf("other"), user.joinedLotteries.map { it.lotteryId })
    }

    @Test fun drawnLotteryCannotBeLeft() {
        `when`(snapshots.getValue("a").getBoolean("drawn")).thenReturn(true)
        assertEquals(false, leave()); assertNoWrites()
        assertEquals(2, user.joinedLotteries.size)
    }

    @Test fun missingMembershipDoesNotChangeOtherEntrants() {
        `when`(snapshots.getValue("a").get("waitlist")).thenReturn(listOf("other"))
        assertEquals(false, leave()); assertNoWrites()
    }

    @Test fun staleLotteryIdCannotLeaveAReplacementLottery() {
        assertEquals(false, leave("old-lottery")); assertNoWrites()
    }

    @Test fun ownerRoleCannotLeaveEvenWithMatchingDeviceId() {
        assertFalse(canLeaveLotteryWaitlist(user.copy(userType = "campsite owner"), Lottery(id = "lot", waitlist = listOf("entrant"))))
        var result: Boolean? = null
        repository.leaveLotteryWaitlist("a", user.copy(userType = "campsite owner"), "lot") { result = it }
        assertEquals(false, result); assertNoWrites()
    }

    @Test fun failedTransactionRetainsSavedAndLocalMembership() {
        `when`(userSnapshot.exists()).thenReturn(false)
        assertEquals(false, leave()); assertNoWrites()
        assertEquals(listOf("lot", "other"), user.joinedLotteries.map { it.lotteryId })
    }

    @Test fun closedRegistrationStillAllowsLeavingBeforeDraw() {
        val closed = Lottery(id = "lot", waitlist = listOf("entrant"), period = Registration(
            LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 1, 2, 0, 0)))
        assertTrue(canLeaveLotteryWaitlist(user, closed))
        assertFalse(canLeaveLotteryWaitlist(user, closed.copy(drawn = true)))
        assertFalse(canLeaveLotteryWaitlist(user, closed.copy(waitlist = emptyMap())))
    }

    private fun prepareJoin() {
        snapshots.values.forEach { snapshot ->
            `when`(snapshot.get("waitlist")).thenReturn(listOf("other"))
            `when`(snapshot.getString("ownerId")).thenReturn("owner")
            `when`(snapshot.getString("registrationStart")).thenReturn(LocalDateTime.now().minusDays(2).toString())
            `when`(snapshot.getString("registrationEnd")).thenReturn(LocalDateTime.now().plusDays(2).toString())
        }
        `when`(userSnapshot.get("joinedLotteries")).thenReturn(listOf(mapOf("lotteryId" to "other", "campsiteName" to "c")))
    }

    private fun join(role: String = "admin"): Boolean? {
        var result: Boolean? = null
        repository.joinLotteryWaitlist("b", user.copy(userType = role, joinedLotteries = emptyList()), "lot") {
            result = it == JoinWaitlistResult.JOINED
        }
        return result
    }

    @Test fun adminCannotJoinLotteryTheyOwnEvenWithMissingCreatorMetadata() {
        prepareJoin()
        `when`(snapshots.getValue("a").getString("ownerId")).thenReturn("entrant")
        assertEquals(false, join())
        assertNoWrites()
    }

    @Test fun adminCreatorCannotJoinLotteryForSomeoneElsesCampsite() {
        prepareJoin()
        `when`(snapshots.getValue("a").getString("createdById")).thenReturn("entrant")
        assertEquals("entrant", repository.parseLottery(snapshots.getValue("a"))?.createdById)
        assertEquals(false, join())
        assertNoWrites()
    }

    @Test fun ownerOfSecondaryCampsiteCannotJoinThroughAnotherCampsite() {
        prepareJoin()
        `when`(parents.getValue("b").getString("ownerID")).thenReturn("entrant")
        assertEquals(false, join())
        assertNoWrites()
    }

    @Test fun ownershipChangedSinceScreenLoadedIsRecheckedInTransaction() {
        prepareJoin()
        val shown = Lottery(id = "lot", ownerId = "owner", campsiteIds = listOf("a", "b"),
            period = Registration(LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(2)))
        assertTrue(LotteryRepository.canJoinWaitlist(shown, "entrant", listOf("owner")))
        `when`(parents.getValue("a").getString("ownerID")).thenReturn("entrant")
        assertEquals(false, join("entrant"))
        assertNoWrites()
    }

    @Test fun adminCanStillJoinAnUnrelatedLottery() {
        prepareJoin()
        assertEquals(true, join())
        copies.values.forEach { verify(tx).update(it, mapOf("waitlist" to listOf("other", "entrant"))) }
    }

    @Test fun entrantCanStillJoinAnUnrelatedLottery() {
        prepareJoin()
        assertEquals(true, join("entrant"))
        copies.values.forEach { verify(tx).update(it, mapOf("waitlist" to listOf("other", "entrant"))) }
    }

    @Test fun campsiteOwnerRoleCannotJoinEvenAnUnrelatedLottery() {
        prepareJoin()
        assertEquals(false, join("campsite owner"))
        assertNoWrites()
    }
    @Test fun fullWaitlistRejectsJoinWithoutChangingEitherMembership() {
        prepareJoin()
        snapshots.values.forEach { snapshot ->
            `when`(snapshot.getLong("waitlistCapacity")).thenReturn(1L)
        }
        assertEquals(false, join("entrant"))
        assertNoWrites()
        assertEquals(listOf("lot", "other"), user.joinedLotteries.map { it.lotteryId })
    }
}
