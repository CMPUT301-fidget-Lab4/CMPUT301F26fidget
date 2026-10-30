package com.example.myapplication

import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class CampsiteRepository {

    private val db = Firebase.firestore
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

    fun addCampsite(campsite: Campsite) {
        campsiteRef.document(campsite.name).set(campsite)
    }


    fun updateCampsite(oldCampsite: Campsite, updatedCampsite: Campsite) {

        val batch = db.batch()
        batch.delete(campsiteRef.document(oldCampsite.name))
        batch.set(campsiteRef.document(updatedCampsite.name), updatedCampsite)
        batch.commit()

    }

    fun deleteCampsite(campsite: Campsite){
        campsiteRef.document(campsite.name).delete()
    }

    fun findCampsite(campsiteName : String) : Campsite?{
        campsites.forEach { campsite ->
            if(campsite.name == campsiteName) {
                return campsite
            }
        }
        return null
    }
}