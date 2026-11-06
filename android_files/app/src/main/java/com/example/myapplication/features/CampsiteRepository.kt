package com.example.myapplication.features

import androidx.compose.runtime.mutableStateMapOf
import com.example.myapplication.features.Campsite
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class CampsiteRepository {

    private val db = Firebase.firestore
    private val editableFields = listOf(
        "location",
        "location.address",
        "location.geoPoint",
        "description",
        "occupancy",
        "photo",
        "tags",
        "rating",
        "termsAndConditions"
    )
    private val campsiteRef = db.collection("campsites")

    // The key is the campsite name (which is also the document id), the value is the campsite.
    // Looking up a campsite by name is now one step instead of looping through a list.
    private val _campsites = mutableStateMapOf<String, Campsite>()

    val campsites : Map<String, Campsite>
        get() = _campsites

    init {
        campsiteRef.addSnapshotListener { snapshot, error ->
            if(error != null){
                return@addSnapshotListener
            }

            _campsites.clear()

            snapshot?.documents?.forEach { document ->
                val campsite = document.toObject(Campsite::class.java)
                if (campsite!= null){
                    _campsites[campsite.name] = campsite
                }

            }
        }
    }




    fun addCampsite(campsite: Campsite): Boolean {
        if (campsite.name.isBlank()) {
            return false   // the name is the document id, so it can't be empty
        }
        campsiteRef.document(campsite.name).set(campsite)
        return true
    }

    /**
     * This update is not good becasue you can't just update a given field and you need to
     * make a new campsite before the old one which you would have to copy everything.
     * */
    fun updateCampsite(oldCampsite: Campsite, updatedCampsite: Campsite) {

        val batch = db.batch()
        batch.delete(campsiteRef.document(oldCampsite.name))
        batch.set(campsiteRef.document(updatedCampsite.name), updatedCampsite)
        batch.commit()

    }


    /**
     * Checks if a campsite exists.
     *
     * This looks in the campsites already loaded from Firestore, it does not
     * make a new request to the database.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @return true if a campsite with that name exists, false if not
     */
    fun campsiteExists(campsiteName: String): Boolean {
        return _campsites.containsKey(campsiteName)
    }

    /**
     * Changes one field of a campsite in Firestore.
     *
     * Only the field you name is changed, every other field stays the same. The
     * campsites map updates by itself once Firestore finishes saving.
     *
     * The name can't be changed with this function because it is the document id.
     * Use [updateCampsite] to rename a campsite.
     *
     * @param campsiteName the name of the campsite to change, which is also its
     * document id
     * @param field the name of the field to change, it must be in [editableFields]
     * @param newValue the new value, it must match the type of the field (for
     * example an Int for maxOccupancy)
     * @return true if the update was sent, false if the campsite does not exist
     * or the field is not allowed
     */
    fun updateCampsiteField(campsiteName: String, field: String, newValue: Any): Boolean {
        if (!campsiteExists(campsiteName)) {
            return false   // no campsite with that name
        }
        if (!editableFields.contains(field)) {
            return false   // not a field we allow changing
        }

        campsiteRef.document(campsiteName).update(field, newValue)
        return true
    }

    /**
     * Updates a campsite's terms and conditions in Firestore.
     *
     * Only the termsAndConditions field is changed, every other field stays the
     * same. The campsites map updates by itself once Firestore finishes saving.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @param text the terms and conditions to set
     * @return true if the update was sent, false if the text is blank or the
     * campsite does not exist
     */
    fun setTermsAndConditions(campsiteName: String, text: String): Boolean {
        if (text.isBlank()) {
            return false   // can't set empty terms
        }
        return updateCampsiteField(campsiteName, "termsAndConditions", text)
    }

    fun deleteCampsite(campsite: Campsite){
        campsiteRef.document(campsite.name).delete()
    }


    /**
     * Finds a campsite by name.
     *
     * This uses the map, so it does not loop through every campsite.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @return the campsite, or null if there is no campsite with that name
     */
    fun findCampsite(campsiteName : String) : Campsite?{
        return _campsites[campsiteName]
    }
}