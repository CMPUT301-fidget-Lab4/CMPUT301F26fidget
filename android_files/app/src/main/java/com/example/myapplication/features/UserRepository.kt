package com.example.myapplication.features

import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.content.Context

import android.provider.Settings.Secure
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UserRepository contains a list of users, populated from Firestore, and several methods for
 * user objects.
 *
 * UserRepository populates the users list from Firestore and continues to listen for
 * updates. UserRepository has add, delete, update and find methods for users.
 */
class UserRepository(currentDeviceID: String) {

    private val _users = mutableStateListOf<User>()

    private val _currentUser = mutableStateOf<User?>(null)
    private val db = Firebase.firestore
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
     * @param newUser The user to save. [User.deviceID] is used as the document ID.
     */
    fun addUser(newUser: User) {
        userDataBase.document(newUser.deviceID).set(newUser)
    }

    /**
     * Removes a user from Firestore.
     *
     * @param user The user to delete. Looks up user by [User.deviceID].
     */
    fun deleteUser(user: User) {
        userDataBase.document(user.deviceID).delete()
    }

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
     * @return If a user with the specified Android ID exists, that [User] object is returned.
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

    init {
        userDataBase.addSnapshotListener { snapshots, error ->
            if (error != null) {
                authState.value = AuthState.Error
                return@addSnapshotListener
            }

            _users.clear()
            _currentUser.value = null

            snapshots?.documents?.forEach { document ->
                val user = document.toObject(User::class.java)
                if (user != null){
                    _users.add(user)

                    if (user.deviceID == currentDeviceID){
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

        authState.value = AuthState.NotRegistered
    }
}

sealed interface AuthState {
    object Loading : AuthState
    object NotRegistered : AuthState
    class LoggedIn(val user: User) : AuthState

    object Error : AuthState
}