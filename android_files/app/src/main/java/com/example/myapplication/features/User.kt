
package com.example.myapplication.features
import com.google.firebase.firestore.DocumentId

/**
 * Records a lottery joined by an entrant.
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
 */
data class User(
    val userType: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",

    @set:DocumentId
    var deviceID: String = "",

    // IDs of campsites associated with this user
    var campsiteIds: List<String> = emptyList(),

    // Lotteries joined by this entrant
    var joinedLotteries: List<JoinedLottery> = emptyList()
) {

    /**
     * Records a lottery that this entrant joined.
     * Only changes the User object locally.
     *
     * @return true if the lottery was added.
     */
    fun addJoinedLottery(
        lotteryId: String,
        campsiteName: String
    ): Boolean {

        if (!userType.equals("entrant", ignoreCase = true)) {
            return false
        }

        if (lotteryId.isBlank() || campsiteName.isBlank()) {
            return false
        }

        // Prevent recording the same lottery twice.
        if (joinedLotteries.any { it.lotteryId == lotteryId }) {
            return false
        }

        joinedLotteries = joinedLotteries + JoinedLottery(
            lotteryId = lotteryId,
            campsiteName = campsiteName
        )

        return true
    }

    /**
     * Checks whether the user can manage campsites.
     */
    fun canManageCampsites(): Boolean {
        return userType.equals("admin", ignoreCase = true) ||
                userType.equals("owner", ignoreCase = true) ||
                userType.equals("campsite owner", ignoreCase = true)
    }

    /**
     * Adds a campsite to the user's list.
     *
     * Returns false if the user does not have permission,
     * the name is invalid, or the campsite is already listed.
     */
    fun addCampsite(campsiteName: String): Boolean {

        if (!canManageCampsites()) {
            return false
        }

        val id = campsiteName.trim()

        if (id.isBlank() || id.contains("/")) {
            return false
        }

        if (campsiteIds.contains(id)) {
            return false
        }

        campsiteIds = campsiteIds + id

        return true
    }

    /**
     * Removes a campsite from the user's list.
     */
    fun removeCampsite(campsiteName: String): Boolean {

        if (!canManageCampsites()) {
            return false
        }

        if (!campsiteIds.contains(campsiteName)) {
            return false
        }

        campsiteIds = campsiteIds - campsiteName

        return true
    }
}
