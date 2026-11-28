package com.example.myapplication.profile

import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.UserRepository
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

/** Runs repository deletion logic; only Firestore and Google Task scheduling are simulated. */
class UserRepositoryDeletionAuditTest {
    @Test fun deletingSelfClearsLocalStateOnlyAfterSuccessfulCommit() = withDeletion("current") { repo, user, commit, tx, ref, _ ->
        val task = repo.deleteUser(user)
        assertEquals(user, repo.currentUser)
        assertNotNull(repo.findUser("current"))
        assertTrue(repo.authState.value is AuthState.LoggedIn)
        verify(tx).delete(ref)
        commit.onSuccess(null)
        assertNull(repo.currentUser)
        assertNull(repo.findUser("current"))
        assertEquals(AuthState.NotRegistered, repo.authState.value)
        assertNotNull(task)
    }

    @Test fun deletingAnotherUserDoesNotLogOutCurrentAdmin() = withDeletion("other") { repo, user, commit, tx, ref, _ ->
        repo.deleteUser(user)
        val admin = repo.currentUser
        assertNotNull(repo.findUser("other"))
        verify(tx).delete(ref)
        commit.onSuccess(null)
        assertNull(repo.findUser("other"))
        assertSame(admin, repo.currentUser)
        assertTrue(repo.authState.value is AuthState.LoggedIn)
        assertSame(admin, (repo.authState.value as AuthState.LoggedIn).user)
        assertNotNull(repo.findUser("current"))
    }

    @Test fun failedCommitDoesNotRemoveProfileOrChangeAuthentication() = withDeletion("current") { repo, user, _, tx, ref, returnedTask ->
        val failure = IllegalStateException("simulated commit rejection")
        `when`(returnedTask.isComplete).thenReturn(true)
        `when`(returnedTask.isSuccessful).thenReturn(false)
        `when`(returnedTask.exception).thenReturn(failure)
        val result = repo.deleteUser(user)
        verify(tx).delete(ref)
        assertSame(returnedTask, result)
        assertTrue(result.isComplete)
        assertFalse(result.isSuccessful)
        assertSame(failure, result.exception)
        assertSame(user, repo.currentUser)
        assertSame(user, repo.findUser("current"))
        assertTrue(repo.authState.value is AuthState.LoggedIn)
        assertSame(user, (repo.authState.value as AuthState.LoggedIn).user)
    }

    private fun withDeletion(targetId: String, check: (UserRepository, User, OnSuccessListener<Void>, Transaction, DocumentReference, Task<Void>) -> Unit) {
        val db = mock(FirebaseFirestore::class.java)
        val tx = mock(Transaction::class.java)
        val users = mock(CollectionReference::class.java)
        `when`(db.collection("users")).thenReturn(users)
        lateinit var listener: EventListener<QuerySnapshot>
        doAnswer { call ->
            listener = call.getArgument(1)
            mock(ListenerRegistration::class.java)
        }.`when`(users).addSnapshotListener(eq(MetadataChanges.INCLUDE), any())
        val repo = UserRepository("current", db)
        val admin = User(deviceID = "current", userType = "admin")
        val other = User(deviceID = "other", userType = "entrant")
        val profiles = listOf(admin, other)
        val refs = profiles.associate { user ->
            user.deviceID to mock(DocumentReference::class.java).also { ref ->
                `when`(ref.path).thenReturn("users/${user.deviceID}")
                `when`(users.document(user.deviceID)).thenReturn(ref)
                `when`(db.document("users/${user.deviceID}")).thenReturn(ref)
            }
        }
        val documents = profiles.map { user ->
            mock(DocumentSnapshot::class.java).also { doc ->
                val ref = refs.getValue(user.deviceID)
                `when`(doc.reference).thenReturn(ref)
                `when`(doc.exists()).thenReturn(true)
                `when`(doc.data).thenReturn(mapOf("name" to user.deviceID))
                `when`(doc.toObject(User::class.java)).thenReturn(user)
                `when`(tx.get(ref)).thenReturn(doc)
            }
        }
        val server = mock(QuerySnapshot::class.java)
        val metadata = mock(SnapshotMetadata::class.java)
        `when`(server.metadata).thenReturn(metadata)
        `when`(server.documents).thenReturn(documents)
        listener.onEvent(server, null)
        fun collection(name: String) = mock(CollectionReference::class.java).also {
            `when`(db.collection(name)).thenReturn(it)
        }
        fun userQuery(collection: CollectionReference, field: String) = mock(Query::class.java).also {
            `when`(collection.whereEqualTo(field, targetId)).thenReturn(it)
        }
        val lottery = mock(Query::class.java)
        `when`(db.collectionGroup("lottery")).thenReturn(lottery)
        val pendingCollection = collection("lotteryDrawResults")
        val pending = mock(Query::class.java)
        `when`(pendingCollection.whereArrayContains("remainingEntrantIds", targetId)).thenReturn(pending)
        val messages = collection("messages")
        val queries = listOf(users, collection("campsites"), collection("bookingSchedules"),
            collection("reservations"), lottery, userQuery(collection("notifications"), "userId"),
            userQuery(messages, "senderId"), userQuery(messages, "recipientId"), pending)
        val snapshots = mutableListOf<QuerySnapshot>()
        val reads = queries.map { query ->
            @Suppress("UNCHECKED_CAST")
            val read = mock(Task::class.java) as Task<QuerySnapshot>
            `when`(query.get(Source.SERVER)).thenReturn(read)
            snapshots += if (query == users) server else mock(QuerySnapshot::class.java).also {
                `when`(it.documents).thenReturn(emptyList())
            }
            read
        }
        @Suppress("UNCHECKED_CAST")
        val aggregate = mock(Task::class.java) as Task<List<QuerySnapshot>>
        @Suppress("UNCHECKED_CAST")
        val committed = mock(Task::class.java) as Task<Void>
        lateinit var success: OnSuccessListener<Void>
        doAnswer { call -> success = call.getArgument(0); committed }.`when`(committed).addOnSuccessListener(any())
        doAnswer { call ->
            call.getArgument<Transaction.Function<Void>>(0).apply(tx)
            committed
        }.`when`(db).runTransaction<Void>(any())
        doAnswer { call ->
            call.getArgument<SuccessContinuation<List<QuerySnapshot>, Void>>(0).then(snapshots)
        }.`when`(aggregate).onSuccessTask<Void>(any())
        mockStatic(Tasks::class.java).use { tasks ->
            tasks.`when`<Task<List<QuerySnapshot>>> { Tasks.whenAllSuccess<QuerySnapshot>(reads) }.thenReturn(aggregate)
            // Delegate commit notification later, after deleteUser has installed the listener.
            val delayed = OnSuccessListener<Void> { success.onSuccess(it) }
            check(repo, profiles.first { it.deviceID == targetId }, delayed, tx, refs.getValue(targetId), committed)
        }
        repo.close()
    }
}
