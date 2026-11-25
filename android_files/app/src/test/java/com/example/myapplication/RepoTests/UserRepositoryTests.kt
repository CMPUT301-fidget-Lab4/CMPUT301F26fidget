package com.example.myapplication.RepoTests

import com.example.myapplication.backEnd.firebaseRepositories.AuthState
import com.example.myapplication.backEnd.firebaseRepositories.UserRepository
import com.example.myapplication.backEnd.userModels.User
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

    @Test fun deletingAnotherProfileKeepsCurrentAdminLoggedIn() {
        val repo = repository()
        val admin = User(deviceID = "current", userType = "admin")
        emit(false, admin, User(deviceID = "other"))
        emit(false, admin)
        assertEquals(admin, repo.currentUser)
        assertTrue(repo.authState.value is AuthState.LoggedIn)
        assertNull(repo.findUser("other"))
    }

    @Test fun closeRemovesListener() {
        val registration = mock(ListenerRegistration::class.java)
        `when`(db.collection("users")).thenReturn(collection)
        `when`(collection.addSnapshotListener(eq(MetadataChanges.INCLUDE), org.mockito.ArgumentMatchers.any())).thenReturn(registration)
        UserRepository("current", db).close()
        verify(registration).remove()
    }
}
