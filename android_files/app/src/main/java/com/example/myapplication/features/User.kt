
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
    var campsiteIds: List<String> = emptyList(),

    // Lotteries joined by this entrant
    var joinedLotteries: List<JoinedLottery> = emptyList()
) {


    /**
     * Removes a lottery that an entrant or admin joined.
     * Only changes the User object locally.
     *
     * @param lotteryId The ID of the lottery to leave.
     * @return true if the lottery was removed, false otherwise.
     */
    fun leaveLottery(lotteryId: String): Boolean {

        // Only entrants and admins can leave lotteries.
        if (!canJoinLottery()) {
            return false
        }

        if (lotteryId.isBlank()) {
            return false
        }

        // Check whether the user joined this lottery.
        if (joinedLotteries.none { it.lotteryId == lotteryId }) {
            return false
        }

        // Remove the lottery from the user's list.
        joinedLotteries = joinedLotteries.filterNot {
            it.lotteryId == lotteryId
        }

        return true
    }


    /**
     * Checks whether the user can join lotteries.
     * Only entrants and admins are allowed.
     */
    fun canJoinLottery(): Boolean {
        val type = userType.trim().lowercase()

        return type == "entrant" || type == "admin"
    }


    /**
     * Records a lottery that this entrant joined.
     * Only changes the User object locally.
     * @return true if the lottery was added.
     */
    fun addJoinedLottery(
        lotteryId: String,
        campsiteName: String
    ): Boolean {

        // Only entrants and admins can join.
        if (!canJoinLottery()) {
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
