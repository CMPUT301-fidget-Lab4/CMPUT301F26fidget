package com.example.myapplication

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore


class UserRepository {

    private val _users = mutableStateListOf<User>()
    private val db = Firebase.firestore
    private val userDataBase = db.collection("users")

    val users: List<User>
        get() = _users


    fun updateUser(newUser: User, oldUser: User) {
        userDataBase.document(oldUser.phoneNumber).set(newUser)
    }

    fun addUser(newUser: User) {
        userDataBase.document(newUser.phoneNumber).set(newUser)
    }

    fun deleteUser(user: User) {
        userDataBase.document(user.phoneNumber).delete()
    }

    fun findUser(phoneNumber: String): User? {
        users.forEach { user ->
            if (user.phoneNumber == phoneNumber){
                return user
            }
        }

        return null
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