package com.example.myapplication.features

import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
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
    private val scheduleRepository: BookingScheduleRepository = BookingScheduleRepository()
) {

    private val db = Firebase.firestore
    private val reservationRef = db.collection("reservations")


    // ---------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------

    /**
     * Listens to every reservation for one entrant.
     *
     * @param entrantId The entrant's device ID.
     * @param onChange Receives the current list, newest first.
     * @return Call remove() on this to stop listening.
     */
    fun listenForEntrant(
        entrantId: String,
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        listen(reservationRef.whereEqualTo("entrantId", entrantId), onChange)

    /**
     * Listens to every reservation for one owner's lotteries.
     *
     * @param ownerId The owner's device ID.
     * @param onChange Receives the current list, newest first.
     * @return Call remove() on this to stop listening.
     */
    fun listenForOwner(
        ownerId: String,
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        listen(reservationRef.whereEqualTo("ownerId", ownerId), onChange)


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
     * @param reservation The INVITED reservation.
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
    ) = update(reservation, onResult) { current, schedule ->
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
     * @param action Applies the change. Returns false if it isn't allowed.
     */
    private fun update(
        reservation: Reservation,
        onResult: (Boolean) -> Unit,
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

            if (!action(current, schedule)) {
                return@runTransaction false
            }

            tx.set(reservationDoc, current)
            if (schedule != null) {
                tx.set(scheduleDoc, BookingScheduleRepository.toMap(schedule))
            }
            true
        }
            .addOnSuccessListener { saved -> onResult(saved) }
            .addOnFailureListener { onResult(false) }
    }

    private fun listen(
        query: Query,
        onChange: (List<Reservation>) -> Unit
    ): ListenerRegistration =
        query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }

            val reservations = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Reservation::class.java)?.apply { id = doc.id }
            }

            onChange(reservations.sortedByDescending { it.createdAt })
        }
}