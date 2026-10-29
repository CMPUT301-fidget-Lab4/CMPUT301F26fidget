package com.example.myapplication.features

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.util.Log
import com.google.firebase.firestore.GeoPoint

class UserRepository {


    private val _users = mutableStateListOf<User>()
    private val _campsites = mutableStateListOf<Campsite>()
    private val db = Firebase.firestore
    private val userDataBase = db.collection("users")
    private val campsiteDataBase = db.collection("Campsites")


    val users: List<User>
        get() = _users


    val campsites: List<Campsite>
        get() = _campsites

    // Loads all campsites owned by this user. Returns false if the user is not an owner or admin
    fun loadCampsites(user: User): Boolean {
        val isOwner = user.userType.equals("owner", ignoreCase = true)
        val isAdmin = user.userType.equals("admin", ignoreCase = true)

        if (!isOwner && !isAdmin) {
            return false
        }

        campsiteDataBase
            .whereEqualTo("ownerId", user.phoneNumber)
            .get()
            .addOnSuccessListener { result ->
                // Clear first so calling this twice doesn't add duplicates
                _campsites.clear()

                for (doc in result) {
                    // location is saved as text like "53.5461,-113.4938"
                    val locationText = doc.getString("location") ?: ""
                    var point: GeoPoint? = null

                    val parts = locationText.split(",")
                    if (parts.size == 2) {
                        val lat = parts[0].trim().toDoubleOrNull()
                        val lon = parts[1].trim().toDoubleOrNull()
                        if (lat != null && lon != null) {
                            point = GeoPoint(lat, lon)
                        }
                    }

                    val location = CampsiteLocation(
                        address = doc.getString("address") ?: "",
                        geoPoint = point
                    )

                    // imageUrl is saved as text, so turn it back into a Uri
                    val imageText = doc.getString("imageUrl") ?: ""
                    var image: Image? = null
                    if (imageText.isNotEmpty()) {
                        image = Image(Uri.parse(imageText))
                    }

                    val campsite = Campsite(
                        campsiteName = doc.getString("name") ?: "",
                        location = location,
                        description = doc.getString("description") ?: "",
                        termCondition = doc.getString("termCondition") ?: "",
                        occupancy = (doc.getLong("occupancy") ?: 0L).toShort(),
                        imageUrl = image,
                        hasLotteryWinner = doc.getBoolean("hasLotteryWinner") ?: false
                    )
                    _campsites.add(campsite)
                }
            }
            .addOnFailureListener { error ->
                Log.d("Firestore", "Failed to load campsites: $error")
            }

        return true
    }

    // Saves a campsite for this user. Returns false if the user is not an owner or admin
    // Though I do need a way so we don't over query the adding a campsite into the collection
    fun addCampsite(user: User, campsite: Campsite): Boolean {
        val isOwner = user.userType.equals("owner", ignoreCase = true)
        val isAdmin = user.userType.equals("admin", ignoreCase = true)

        if (!isOwner && !isAdmin) {
            return false
        }

        val data = hashMapOf<String, Any>(
            "name" to campsite.campsiteName,
            "description" to campsite.description,
            "termCondition" to campsite.termCondition,
            "occupancy" to campsite.occupancy.toInt(),
            "ownerId" to user.phoneNumber,
            "hasLotteryWinner" to campsite.hasLotteryWinner
        )

        // Save the location parts that exist, both as strings
        val location = campsite.location
        if (location != null) {
            data["address"] = location.address

            val point = location.geoPoint
            if (point != null) {
                data["location"] = "${point.latitude},${point.longitude}"
            }
        }

        // Save the image uri as a string if there is one
        val image = campsite.imageUrl
        if (image != null) {
            val imageUri = image.imageURI
            if (imageUri != null) {
                data["imageUrl"] = imageUri.toString()
            }
        }

        campsiteDataBase.add(data)
            .addOnSuccessListener {
                // Reload so the list shows the new campsite
                loadCampsites(user)
            }
            .addOnFailureListener { error ->
                Log.d("Firestore", "Failed to add campsite: $error")
            }

        return true
    }
    // Deletes one of this user's campsites. Returns false if the user is not an owner or admin,
// or if the campsite already has a lottery winner
    fun removeCampsite(user: User, campsite: Campsite): Boolean {
        val isOwner = user.userType.equals("owner", ignoreCase = true)
        val isAdmin = user.userType.equals("admin", ignoreCase = true)

        if (!isOwner && !isAdmin) {
            return false
        }

        // Quick check using the campsite we already have in the app
        if (campsite.hasLotteryWinner) {
            return false
        }

        // Find the document that belongs to this user and has this name
        campsiteDataBase
            .whereEqualTo("ownerId", user.phoneNumber)
            .whereEqualTo("name", campsite.campsiteName)
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    // Check the database too, in case the app's copy is out of date
                    val hasWinner = doc.getBoolean("hasLotteryWinner") ?: false

                    if (hasWinner) {
                        // Depends on how I want to store everything
                       // Log.d("Firestore", "Campsite has a lottery winner, not deleted")
                    } else {
                        doc.reference.delete()
                            .addOnSuccessListener {
                                // Remove it from the list so the screen updates
                                _campsites.removeAll { it.campsiteName == campsite.campsiteName }
                            }
                            .addOnFailureListener { error ->
                                Log.d("Firestore", "Failed to delete campsite: $error")
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.d("Firestore", "Failed to find campsite: $error")
            }

        return true
    }

    fun updateUser(newUser: User, oldUser: User) {
        userDataBase.document(oldUser.phoneNumber).set(newUser)
    }

    fun addUser(newUser: User) {
        userDataBase.document(newUser.phoneNumber).set(newUser)
    }

    fun deleteUser(user: User) {
        userDataBase.document(user.phoneNumber).delete()
    }

    // This needs to be fixed
    // Why are you doing a linear search O(N)
    // when you can just check the user in the database by id
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