package com.example.myapplication.backEnd.firebaseRepositories

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import android.content.Context

import android.provider.Settings.Secure
import androidx.compose.runtime.mutableStateOf
import com.example.myapplication.backEnd.userModels.User
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UserRepository contains a list of users, populated from Firestore, and several methods for
 * user objects.
 *
 * UserRepository populates the users list from Firestore and continues to listen for
 * updates. UserRepository has add, delete, update and find methods for users.
 */
class UserRepository(private val currentDeviceID: String, private val db: FirebaseFirestore = Firebase.firestore) {

    private val _users = mutableStateListOf<User>()

    private val _currentUser = mutableStateOf<User?>(null)
    private val userDataBase = db.collection("users")
     var authState = MutableStateFlow<AuthState>(AuthState.Loading)



    /**
     * List of users currently in Firestore, updated by a snapshot listener.
     */
    val users: List<User>
        get() = _users

    val currentUser: User?
        get() = _currentUser.value

    /**
     * Adds a user to Firestore.
     *
     * @param newUser The user to save. [com.example.myapplication.Back.deviceID] is used as the document ID.
     */
    fun addUser(newUser: User) {
        userDataBase.document(newUser.deviceID).set(newUser)
    }

    /**
     * Deletes a profile and applies related cleanup through [ProfileDeletionPlan].
     *
     * Reads users, campsites, lotteries, schedules, reservations, notifications, and
     * messages from the server. Re-reads discovered documents in a transaction, then
     * commits cleanup and profile deletion together. Requires server access and
     * permission to read and change the affected collections.
     *
     * Rejects more than 450 discovered document references. Transaction retries
     * protect discovered records against concurrent edits, but records created after
     * the initial queries may be missed. Larger datasets and coordination with
     * concurrent creation require server-side cleanup.
     *
     * On success, removes the deleted user from the local list. Clears the current
     * profile and authentication state only when deleting this device's profile;
     * deleting another user does not log out the current administrator. Failure
     * does not explicitly clear local profile state.
     *
     * @param user Profile to delete, identified by [User.deviceID].
     * @return Task that succeeds after cleanup commits, or fails for an invalid ID,
     * failed server access, the document limit, or transaction errors.
     */
    fun deleteUser(user: User): Task<Void> {
        if (user.deviceID.isBlank() || user.deviceID.contains('/')) {
            return Tasks.forException(IllegalArgumentException("Invalid profile ID"))
        }
        val reads = listOf("users", "campsites", "bookingSchedules", "reservations", "notifications", "messages")
            .map { db.collection(it).get(Source.SERVER) } +
            db.collectionGroup("lottery").get(Source.SERVER)
        return Tasks.whenAllSuccess<QuerySnapshot>(reads)
            .onSuccessTask { snapshots ->
                val refs = (snapshots.flatMap { it.documents }.map { it.reference } +
                    userDataBase.document(user.deviceID)).distinctBy { it.path }
                check(refs.size <= 450) { "Profile cleanup exceeds the client transaction limit; server cleanup is required." }
                db.runTransaction<Void> { transaction ->
                    val docs = refs.map { transaction.get(it) }
                        .filter { it.exists() }.associate { it.reference.path to it.data.orEmpty() }
                    ProfileDeletionPlan.build(user.deviceID, docs, System.currentTimeMillis()).forEach { (path, fields) ->
                        if (fields == null) transaction.delete(db.document(path))
                        else transaction.update(db.document(path), fields)
                    }
                    null
                }
            }.addOnSuccessListener {
                _users.removeAll { it.deviceID == user.deviceID }
                if (currentDeviceID == user.deviceID) {
                    _currentUser.value = null
                    authState.value = AuthState.NotRegistered
                }
            }
    }

    /**
     * Stops listening for user changes in Firestore.
     *
     * Call when this repository is no longer used. This releases the listener without
     * deleting user data, clearing the last loaded state, or cancelling pending writes.
     */
    fun close() = userListener.remove()

    /**
     * Updates a user in Firestore.
     *
     * @param oldUser The user document in Firestore being replaced.
     * @param newUser The user object replacing the existing document in Firestore.
     */
    fun updateUser(newUser: User, oldUser: User) {
        userDataBase.document(oldUser.deviceID).set(newUser)
    }

    /**
     * Fetches a user document from _users.
     *
     * @param deviceID The unique device ID to identifier the user object by.
     * @return If a user with the specified Android ID exists, that [com.example.myapplication.Back] object is returned.
     * Otherwise, null is returned.
     */
    fun findUser(deviceID: String): User? {
        users.forEach { user ->
            if (user.deviceID == deviceID){
                return user
            }
        }

        return null
    }

    /**
     * Gets a user's device ID.
     *
     * @param context User's current device context. Stores unique Android ID.
     * @return Returns the user's Android ID as a string.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceID(context: Context): String {
        return Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        )
    }

    /**
     * Fetches the UserRepositories AuthState value
     *
     * @return Returns the UserRepositories AuthState value
     */

    private val userListener = userDataBase.addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, error ->
        if (error != null) {
            authState.value = AuthState.Error
            return@addSnapshotListener
        }

        // Cached profiles may have been deleted on another device. Authenticate only
        // after a server snapshot, including when an empty server result matches cache.
        if (snapshots == null || snapshots.metadata.isFromCache) return@addSnapshotListener
        _users.clear()
        _currentUser.value = null

        snapshots.documents.forEach { document ->
            val user = try {
                document.toObject(User::class.java)
            } catch (e: Exception) {
                Log.e("UserRepository", "Skipping doc ${document.id}: ${e.message}", e)
                null
            }
            if (user != null) {
                _users.add(user)
                if (user.deviceID == currentDeviceID) {
                    _currentUser.value = user
                }
            }
        }

        if (_currentUser.value != null) {
            authState.value = AuthState.LoggedIn(_currentUser.value!!)
        }

        else {
            authState.value = AuthState.NotRegistered
        }
    }
}

sealed interface AuthState {
    object Loading : AuthState
    object NotRegistered : AuthState
    class LoggedIn(val user: User) : AuthState

    object Error : AuthState
}