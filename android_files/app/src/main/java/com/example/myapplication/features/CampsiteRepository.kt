package com.example.myapplication.features

import androidx.compose.runtime.mutableStateListOf
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
        "inLottery",
        "reservationSet",
        "bookingSet"
    )
    private val campsiteRef = db.collection("campsites")
    private val _campsites = mutableStateListOf<Campsite>()

    val campsites : List<Campsite>
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
                    _campsites.add(campsite)
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
     * This looks through the campsites already loaded from Firestore, it does not
     * make a new request to the database.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @return true if a campsite with that name exists, false if not
     */
    fun campsiteExists(campsiteName: String): Boolean {
        return findCampsite(campsiteName) != null
    }

    /**
     * Changes one field of a campsite in Firestore.
     *
     * Only the field you name is changed, every other field stays the same. The
     * campsites list updates by itself once Firestore finishes saving.
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

    fun deleteCampsite(campsite: Campsite){
        campsiteRef.document(campsite.name).delete()
    }


    /**
     * Why don't you ues a hashmap? It would make search way faster
     * */
    fun findCampsite(campsiteName : String) : Campsite?{
        campsites.forEach { campsite ->
            if(campsite.name == campsiteName) {
                return campsite
            }
        }
        return null
    }
    /**
     * Checks if any campsite in the list is already in a lottery.
     *
     * @param names the campsite names to check
     * @return true if at least one of them is in a lottery, false if none are
     */
    fun anyInLottery(names: List<String>): Boolean {
        for (name in names) {
            if (!campsiteExists(name)) {
                return true   // doesn't exist
            }
            if (isInLottery(name)) {
                return true   // already in a lottery
            }
        }
        return false
    }
    /**
     * Checks if a campsite is already in a lottery.
     *
     * This looks at the inLottery flag of the campsites already loaded from
     * Firestore, it does not make a new request to the database.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @return true if the campsite exists and is in a lottery, false if it is not
     * in a lottery or does not exist
     */
    fun isInLottery(campsiteName: String): Boolean {
        val campsite = findCampsite(campsiteName)
        if (campsite == null) {
            return false
        }
        return campsite.inLottery
    }
}