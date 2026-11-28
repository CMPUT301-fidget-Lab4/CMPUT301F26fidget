package com.example.myapplication.profile

import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.lottery.Registration
import com.example.myapplication.features.profile.JoinedLottery
import java.time.LocalDateTime
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.UserRepository
import com.example.myapplication.features.profile.User
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class UserRepositoryTests {
    private val db = mock(FirebaseFirestore::class.java)
    private val collection = mock(CollectionReference::class.java)
    private lateinit var listener: EventListener<QuerySnapshot>

    private fun repository(): UserRepository {
        `when`(db.collection("users")).thenReturn(collection)
        doAnswer { call ->
            listener = call.getArgument(1)
            mock(ListenerRegistration::class.java)
        }.`when`(collection).addSnapshotListener(eq(MetadataChanges.INCLUDE), org.mockito.ArgumentMatchers.any())
        return UserRepository("current", db)
    }

    private fun emit(cached: Boolean, vararg users: User) {
        val snapshot = mock(QuerySnapshot::class.java)
        val metadata = mock(SnapshotMetadata::class.java)
        `when`(metadata.isFromCache).thenReturn(cached)
        `when`(snapshot.metadata).thenReturn(metadata)
        val documents = users.map { user ->
            mock(DocumentSnapshot::class.java).also { `when`(it.toObject(User::class.java)).thenReturn(user) }
        }
        `when`(snapshot.documents).thenReturn(documents)
        listener.onEvent(snapshot, null)
    }

    @Test fun cachedDeletedProfileCannotLogInOnRestart() {
        val repo = repository()
        emit(true, User(deviceID = "current", name = "Deleted"))
        assertNull(repo.currentUser)
        assertTrue(repo.users.isEmpty())
        assertEquals(AuthState.Loading, repo.authState.value)
        emit(false)
        assertEquals(AuthState.NotRegistered, repo.authState.value)
        assertNull(repo.findUser("current"))
    }

    @Test fun serverDeletionClearsPreviouslyLoadedProfileAndFindUser() {
        val repo = repository()
        emit(false, User(deviceID = "current"))
        assertNotNull(repo.currentUser)
        emit(false)
        assertTrue(repo.users.isEmpty())
        assertNull(repo.currentUser)
        assertNull(repo.findUser("current"))
        assertEquals(AuthState.NotRegistered, repo.authState.value)
    }

    @Test fun serverSnapshotRemovingAnotherProfileKeepsCurrentAdminLoggedIn() {
        val repo = repository()
        val admin = User(deviceID = "current", userType = "admin")
        emit(false, admin, User(deviceID = "other"))
        emit(false, admin)
        assertEquals(admin, repo.currentUser)
        assertTrue(repo.authState.value is AuthState.LoggedIn)
        assertNull(repo.findUser("other"))
    }

    private fun lottery(id: String, day: Int = 1) = Lottery(
        id = id, ownerId = "owner", campsiteIds = listOf("camp"),
        period = Registration(LocalDateTime.of(2035, 1, day, 0, 0), LocalDateTime.of(2035, 2, 1, 0, 0))
    )

    @Test fun allSupportedRolesReadOnlyTheirJoinedLotteries() {
        val repo = repository()
        val older = lottery("older")
        val newer = lottery("newer", 2)
        val unjoined = lottery("unjoined", 3)
        val records = listOf(older, newer, unjoined).associateBy { it.id }
        for (role in listOf(" Entrant ", "ADMIN", " Campsite Owner ")) {
            emit(false, User(deviceID = "current", userType = role,
                joinedLotteries = listOf(JoinedLottery("older", "camp"), JoinedLottery("missing", "camp"),
                    JoinedLottery("newer", "camp"), JoinedLottery("older", "camp"))),
                User(deviceID = "someoneElse", userType = role,
                    joinedLotteries = listOf(JoinedLottery("unjoined", "camp"))))
            assertEquals(listOf(newer, older), repo.getJoinedLotteries(records))
        }
        verify(collection, never()).get()
    }

    @Test fun personalListFollowsCurrentProfileAndLiveLotteryUpdates() {
        val repo = repository()
        val original = lottery("joined")
        val records = mapOf("joined" to original)
        assertTrue(repo.getJoinedLotteries(records).isEmpty())
        val profile = User(deviceID = "current", userType = "entrant",
            joinedLotteries = listOf(JoinedLottery("joined", "camp")))
        emit(false, profile)
        assertSame(original, repo.getJoinedLotteries(records).single())
        val updated = original.copy(drawn = true)
        assertSame(updated, repo.getJoinedLotteries(mapOf("joined" to updated)).single())
        emit(false, profile.copy(joinedLotteries = emptyList()))
        assertTrue(repo.getJoinedLotteries(records).isEmpty())
        emit(false, profile.copy(userType = "unknown"))
        assertTrue(repo.getJoinedLotteries(records).isEmpty())
        emit(false)
        assertTrue(repo.getJoinedLotteries(records).isEmpty())
    }

    @Test fun adminOverviewResolvesEveryMemberAndCampsiteWithoutPersonalMembership() {
        val repo = repository()
        val profile = User(deviceID = "member", name = "Current name", email = "current@example.com")
        val active = lottery("active").copy(waitlist = mapOf("member" to profile.copy(name = "Stale"), "missing" to null))
        val newer = lottery("newer", 2)
        val drawn = lottery("drawn", 3).copy(drawn = true)
        val records = listOf(active, newer, drawn).associateBy { it.id }
        emit(false, User(deviceID = "current", userType = " ADMIN "), profile)
        val result = repo.getAllActiveLotteryWaitlists(records)
        assertEquals(listOf("newer", "active"), result.map { it.id })
        assertEquals(listOf("camp"), result.last().campsiteIds)
        assertEquals(setOf("member", "missing"), result.last().waitlist.keys)
        assertSame(profile, result.last().waitlist["member"])
        assertTrue(result.last().waitlist.containsKey("missing"))
        assertNull(result.last().waitlist["missing"])
        assertTrue(repo.getJoinedLotteries(records).isEmpty())
        emit(false, User(deviceID = "current", userType = "admin"))
        assertNull(repo.getAllActiveLotteryWaitlists(records).last().waitlist["member"])
    }

    @Test fun nonAdminsAndSignedOutUsersCannotReadTheAdminOverview() {
        val repo = repository()
        val records = mapOf("lot" to lottery("lot"))
        assertTrue(repo.getAllActiveLotteryWaitlists(records).isEmpty())
        for (role in listOf("entrant", "campsite owner", "unknown")) {
            emit(false, User(deviceID = "current", userType = role))
            assertTrue(repo.getAllActiveLotteryWaitlists(records).isEmpty())
        }
        emit(false, User(deviceID = "current", userType = "admin"))
        assertEquals(1, repo.getAllActiveLotteryWaitlists(records).size)
        emit(false)
        assertTrue(repo.getAllActiveLotteryWaitlists(records).isEmpty())
    }

    @Test fun waitlistUsesCurrentUsersAndRevokesAccessWhenRoleOrOwnershipChanges() {
        val repo = repository()
        val profile = User(deviceID = "member", name = "Latest")
        val selected = lottery("lot").copy(waitlist = mapOf("member" to profile.copy(name = "Stale")))
        val campsites = mapOf("camp" to Campsite(name = "camp", ownerID = "current"))
        emit(false, User(deviceID = "current", userType = "campsite owner"), profile)
        assertSame(profile, repo.getLotteryWaitlist(selected, campsites)!!.waitlist["member"])
        assertNull(repo.getLotteryWaitlist(selected, emptyMap()))
        emit(false, User(deviceID = "current", userType = "entrant"), profile)
        assertNull(repo.getLotteryWaitlist(selected, campsites))
        emit(false, User(deviceID = "current", userType = "admin"))
        val unresolved = repo.getLotteryWaitlist(selected, campsites)!!
        assertEquals(setOf("member"), unresolved.waitlist.keys)
        assertNull(unresolved.waitlist["member"])
        assertNull(repo.getLotteryWaitlist(null, campsites))
        emit(false)
        assertNull(repo.getLotteryWaitlist(selected, campsites))
    }

    @Test fun closeRemovesListener() {
        val registration = mock(ListenerRegistration::class.java)
        `when`(db.collection("users")).thenReturn(collection)
        `when`(collection.addSnapshotListener(eq(MetadataChanges.INCLUDE), org.mockito.ArgumentMatchers.any())).thenReturn(registration)
        UserRepository("current", db).close()
        verify(registration).remove()
    }
}
