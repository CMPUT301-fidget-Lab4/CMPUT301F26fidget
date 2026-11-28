package com.example.myapplication.data.firebase

import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.campsite.CampsiteLocation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class CampsiteRepository(private val db: FirebaseFirestore = Firebase.firestore,
    sharedLotteryRepository: LotteryRepository? = null) {
    private val campsiteRef = db.collection("campsites")

    // Change the list of campsites to a hash map to make lookup faster then looping
    private val _campsites = mutableStateListOf<Campsite>()

    private val _campsitesMap = mutableStateMapOf<String, Campsite>()


    val campsites : List<Campsite>
        get() = _campsites

    val campsitesMap: Map<String, Campsite>
        get() = _campsitesMap

    var bookingSchedules by mutableStateOf(BookingScheduleRepository.Catalog())
        private set
    private val scheduleRepository = BookingScheduleRepository(db)
    private val lotteryRepository = sharedLotteryRepository ?: LotteryRepository(db, campsiteLookup = { _campsitesMap[it] })
    private val lotteryCollectionListener = lotteryRepository.observeLotteries { populateCollections() }

    private fun populateCollections() {
        _campsitesMap.forEach { (id, campsite) ->
            campsite.lotteryCollection = lotteryRepository.allLotteries.values.filter { id in it.campsites }
            campsite.bookingSchedules = bookingSchedules.byCampsite?.get(id).orEmpty().toMutableList()
        }
    }

    private var scheduleListener: ListenerRegistration? = null
    private var scheduleGeneration = 0
    private var closed = false
    private val campsiteListener: ListenerRegistration

    fun close() {
        closed = true
        scheduleGeneration++
        scheduleListener?.remove()
        campsiteListener.remove()
        lotteryCollectionListener.remove()
        lotteryRepository.close()
    }

    fun retryBookingSchedules() {
        if (closed) return
        val generation = ++scheduleGeneration
        scheduleListener?.remove()
        bookingSchedules = bookingSchedules.copy(checking = true, error = null)
        scheduleListener = scheduleRepository.observeSchedules(
            onError = { error ->
                if (generation == scheduleGeneration) {
                    bookingSchedules = bookingSchedules.copy(checking = false, error = error)
                }
            },
            onChange = { catalog ->
                if (generation == scheduleGeneration) {
                    bookingSchedules = catalog
                    populateCollections()
                }
            }
        )
    }

    init {
        lotteryRepository.startListening()
        retryBookingSchedules()
        campsiteListener = campsiteRef.addSnapshotListener { snapshot, error ->
            if(error != null){
                return@addSnapshotListener
            }

            _campsites.clear()
            _campsitesMap.clear()

            snapshot?.documents?.forEach { document ->
                val campsite = document.toObject(Campsite::class.java)
                if (campsite!= null){
                    _campsites.add(campsite)
                    _campsitesMap[document.id] = campsite

                }

            }
            lotteryRepository.refreshReferences()
            populateCollections()
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

    fun updateCampsiteDetails(
        campsiteName: String,
        location: CampsiteLocation,
        description: String,
        occupancy: Int,
        tags: String,
        termsAndConditions: String? = null
    ): Task<Void> {
        if (campsiteName.isBlank() || '/' in campsiteName || occupancy < 1) {
            return Tasks.forException(IllegalArgumentException("Set a valid campsite and a positive occupancy."))
        }
        val document = campsiteRef.document(campsiteName)
        val changes = mapOf(
            "location" to location,
            "description" to description,
            "occupancy" to occupancy,
            "tags" to tags
        ) + (termsAndConditions?.let { mapOf("termsAndConditions" to it) } ?: emptyMap())
        return db.runTransaction { transaction ->
            val current = transaction.get(document)
            check(current.exists()) { "The campsite no longer exists." }
            if (occupancy < (current.getLong("occupancy") ?: 0L)) {
                lotteryRepository.checkCampsiteOccupancyReduction(transaction, campsiteName, occupancy)
            }
            transaction.update(document, changes)
            null
        }
    }

    fun deleteCampsite(campsite: Campsite){
        campsiteRef.document(campsite.name).delete()
    }

    fun deleteCampsitesByOwner(ownerID: String) {
        campsitesMap.values.filter { it.ownerID == ownerID }
            .forEach(::deleteCampsite)
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
