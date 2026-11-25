package com.example.myapplication.backEnd.firebaseRepositories

import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Reservation
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime

/**
 * Reads and updates reservations in Firestore.
 *
 * Each reservation is its own document in the "reservations" collection.
 * Anything that changes a booking (reserve, cancel, reject) runs in a
 * transaction together with the lottery's booking schedule, so the
 * reservation and the schedule always agree and a campsite can't be
 * overfilled.
 *
 * @param scheduleRepository Used to find and rebuild booking schedules.
 */
class ReservationRepository(
    private val scheduleRepository: BookingScheduleRepository = BookingScheduleRepository(),
    private val db: FirebaseFirestore = Firebase.firestore
) {

    private val reservationRef = db.collection("reservations")


    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    /**
     * Listens to every reservation for one entrant.
     *
     * @param entrantId The entrant's device ID.
     * @param onError Receives read or document parsing errors.
     * @param onCacheStatus True for cached results; false after server confirmation.
     * @param onChange Receives the current list, newest first.
     * @return Call remove() on this to stop listening.
     */
    fun listenForEntrant(
        entrantId: String,
        onError: (Exception) -> Unit = {},
        onCacheStatus: (Boolean) -> Unit = {},
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        listen(reservationRef.whereEqualTo("entrantId", entrantId), onError, onCacheStatus, onChange)

    /**
     * Listens to every reservation for one owner's lotteries.
     *
     * @param ownerId The owner's device ID.
     * @param onError Receives read or document parsing errors.
     * @param onCacheStatus True for cached results; false after server confirmation.
     * @param onChange Receives the current list, newest first.
     * @return Call remove() on this to stop listening.
     */
    fun listenForOwner(
        ownerId: String,
        onError: (Exception) -> Unit = {},
        onCacheStatus: (Boolean) -> Unit = {},
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        listen(reservationRef.whereEqualTo("ownerId", ownerId), onError, onCacheStatus, onChange)


    /** Admin management; Firestore rules must also permit access. */
    fun listenForAdmin(
        onError: (Exception) -> Unit = {},
        onCacheStatus: (Boolean) -> Unit = {},
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration = listen(reservationRef, onError, onCacheStatus, onChange)

    // ---------------------------------------------------------------
    // Creating
    // ---------------------------------------------------------------

    /**
     * Saves the invitations made by Lottery.createInvitations().
     * Call this right after the winners are drawn and saved.
     *
     * @param invitations One INVITED reservation per winner.
     * @param onResult true if all of them were saved.
     */
    fun addInvitations(
        invitations: List<Reservation>,
        onResult: (Boolean) -> Unit
    ) {
        if (invitations.isEmpty()) {
            onResult(true)
            return
        }

        val batch = db.batch()
        for (invitation in invitations) {
            val doc = reservationRef.document()   // new id
            invitation.id = doc.id
            batch.set(doc, invitation)
        }

        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }


    // ---------------------------------------------------------------
    // Entrant actions
    // ---------------------------------------------------------------

    /**
     * Entrant accepts their invitation by picking a campsite and dates.
     *
     * @param reservation An INVITED or entrant-CANCELLED reservation.
     * @param campsiteId The campsite they picked.
     * @param start Start of their stay.
     * @param end End of their stay.
     * @param occupancy Maximum number of people the campsite holds.
     * @param onResult true if reserved, false if not allowed or the dates are full.
     */
    fun reserve(
        reservation: Reservation,
        campsiteId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        occupancy: Int,
        onResult: (Boolean) -> Unit
    ) = update(reservation, onResult, requiredCampsiteId = campsiteId) { current, schedule ->
        schedule != null &&
                current.reserve(schedule, campsiteId, start, end, occupancy)
    }

    /**
     * Entrant says no to their invitation.
     */
    fun decline(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation, onResult) { current, _ ->
        current.decline()
    }

    /**
     * Entrant cancels a reservation before their stay starts.
     */
    fun cancel(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation, onResult) { current, schedule ->
        schedule != null && current.cancel(schedule)
    }


    // ---------------------------------------------------------------
    // Owner actions
    // ---------------------------------------------------------------

    /**
     * Owner rejects an invitation or reservation.
     */
    fun reject(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation, onResult) { current, schedule ->
        schedule != null && current.reject(schedule)
    }

    /** Owner/admin restores an invitation without reclaiming its old booking dates. */
    fun unreject(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation, onResult) { current, _ ->
        current.unreject()
    }


    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Loads the latest reservation and schedule, applies a change,
     * and saves both, all in one transaction.
     *
     * If someone else changes either document in the middle, Firestore
     * reruns the whole thing with the new data, so the checks in
     * Reservation and BookingSchedule always see the latest bookings.
     *
     * @param requiredCampsiteId For booking actions, verifies this campsite still exists
     * inside the transaction so deletion causes a retry or a rejected booking.
     * @param action Applies the change. Returns false if it isn't allowed.
     */
    private fun update(
        reservation: Reservation,
        onResult: (Boolean) -> Unit,
        requiredCampsiteId: String? = null,
        action: (Reservation, BookingSchedule<Lottery>?) -> Boolean
    ) {
        if (reservation.id.isBlank()) {
            onResult(false)
            return
        }

        val reservationDoc = reservationRef.document(reservation.id)
        val scheduleDoc = scheduleRepository.scheduleDoc(reservation.lotteryId)

        db.runTransaction { tx ->
            // All reads must happen before any writes
            val current = tx.get(reservationDoc)
                .toObject(Reservation::class.java)
                ?.apply { id = reservationDoc.id }
                ?: return@runTransaction false

            val schedule = BookingScheduleRepository.fromSnapshot(tx.get(scheduleDoc))
            // Retained schedules and stale UI state must not allow booking a deleted campsite.
            if (requiredCampsiteId != null &&
                !tx.get(db.collection("campsites").document(requiredCampsiteId)).exists()) {
                return@runTransaction false
            }

            if (!action(current, schedule)) {
                return@runTransaction false
            }

            tx.set(reservationDoc, current)
            if (schedule != null) {
                tx.set(scheduleDoc, BookingScheduleRepository.toMap(schedule))
            }
            true
        }
            .addOnSuccessListener { saved ->
                // TODO(notifications): If saved, on reserve/rebook notify the entrant and owner of confirmed dates.
                // TODO(notifications): If saved, on entrant cancellation/decline notify the owner and confirm to the entrant.
                // TODO(notifications): If saved, on owner/admin rejection notify the entrant and owner.
                // TODO(notifications): If saved, on unreject notify the entrant and owner that the invitation is restored and dates must be selected again.
                // Dispatch only after a successful commit, never inside the retried transaction.
                onResult(saved)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun listen(
        query: Query,
        onError: (Exception) -> Unit,
        onCacheStatus: (Boolean) -> Unit,
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                onError(IllegalStateException("No reservation snapshot was returned."))
                return@addSnapshotListener
            }

            val reservations = try {
                snapshot.documents.map { doc ->
                    checkNotNull(doc.toObject(Reservation::class.java)) {
                        "Reservation ${doc.id} could not be read."
                    }.apply { id = doc.id }
                }.sortedByDescending { it.createdAt }
            } catch (exception: Exception) {
                onError(exception)
                return@addSnapshotListener
            }

            onCacheStatus(snapshot.metadata.isFromCache)
            onChange(reservations)
        }
}
