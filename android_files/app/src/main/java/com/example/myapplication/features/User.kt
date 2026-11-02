package com.example.myapplication.features

import com.google.firebase.firestore.DocumentId

/**
 * A collection of user information
 *
 * Stored in Firestore, each user is a document with the deviceID as the document ID.
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
    val email : String = "",
    val phoneNumber : String = "",
    @set:DocumentId
    var deviceID : String = "",
    // Names of campsites associated with this user
    var campsiteIds: List<String> = emptyList()
) {
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



