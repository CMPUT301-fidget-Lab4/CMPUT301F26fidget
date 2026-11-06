package com.example.myapplication.features

import com.google.firebase.firestore.DocumentId

/**
 * A collection of user information.
 * Stored in Firestore, each user is a document
 * with the deviceID as the document ID.
 * @property userType The role of the user ( admin)
 * @property name The user's display name
 * @property email The user's email address
 * @property phoneNumber The user's phone number
 * @property deviceID The user's unique Android ID
 * @property campsiteIds  IDs of campsites associated with this user. Only for admins/owners.
 * @property joinedLotteries A list of the lotteries that a entrant/admin is in
 */
data class AdminUser(
    val userType: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    @set:DocumentId
    var deviceID: String = "",
    var campsiteIds: List<String> = emptyList(),
    var joinedLotteries: List<JoinedLottery> = emptyList()
)