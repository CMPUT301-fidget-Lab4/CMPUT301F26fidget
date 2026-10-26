package com.example.myapplication

import com.google.firebase.firestore.DocumentId

class User(
    val userType: String = "",
    val name: String = "",
    val email : String = "",
    val phoneNumber : String = "",
    @set:DocumentId
    var phoneID : String = ""
)