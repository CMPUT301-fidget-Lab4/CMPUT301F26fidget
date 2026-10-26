package com.example.myapplication

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.content.Context
import android.provider.Settings


class UserRepository {

    private val _users = mutableStateListOf<User>()
    private val db = Firebase.firestore
    private val userDataBase = db.collection("users")

    val users: List<User>
        get() = _users


    fun updateUser(newUser: User, oldUser: User) {
        userDataBase.document(oldUser.phoneID).set(newUser)
    }

    fun addUser(newUser: User) {
        userDataBase.document(newUser.phoneID).set(newUser)
    }

    fun deleteUser(user: User) {
        userDataBase.document(user.phoneID).delete()
    }

    fun findUser(deviceID: String): User? {
        users.forEach { user ->
            if (user.phoneID == deviceID){
                return user
            }
        }

        return null
    }

    fun getDeviceID(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    init {
        userDataBase.addSnapshotListener { snapshots, error ->
            if (error != null) {
                return@addSnapshotListener
            }

            _users.clear()

            snapshots?.documents?.forEach { document ->
                val user = document.toObject(User::class.java)
                if (user != null){
                    _users.add(user)
                }
            }
        }
    }
}