package com.example.myapplication

import com.google.firebase.firestore.DocumentId

class User(
    val userType: String = "",
    val name: String = "",
    val email : String = "",
    @set:DocumentId
    var phoneNumber : String = "",
//    val phoneID : String = ""
)