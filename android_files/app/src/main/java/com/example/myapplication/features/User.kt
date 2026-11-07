
package com.example.myapplication.features
import com.google.firebase.firestore.DocumentId

/**
 * Records a lottery joined by an entrant/Admin
 */
data class JoinedLottery(
    var lotteryId: String = "",
    var campsiteName: String = ""
)

/**
 * A collection of user information.
 *
 * Stored in Firestore, each user is a document
 * with the deviceID as the document ID.
 *
 * @property userType The role of the user (entrant, campsite owner, admin)
 * @property name The user's display name
 * @property email The user's email address
 * @property phoneNumber The user's phone number
 * @property deviceID The user's unique Android ID
 * @property campsiteIds  IDs of campsites associated with this user. Only for admins/owners.
 * @property joinedLotteries A list of the lotteries that a entrant/admin is in
 */
data class User(
    val userType: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",

    @set:DocumentId
    var deviceID: String = "",

    // IDs of campsites associated with this user
    var campsiteIds: Map<String, Campsite> = emptyMap(),
    // Lotteries joined by this entrant
    var joinedLotteries: List<JoinedLottery> = emptyList()
) {

}
