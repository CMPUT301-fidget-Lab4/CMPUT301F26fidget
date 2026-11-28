package com.example.myapplication.data.firebase

import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
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
    // Entrant actions
    // ---------------------------------------------------------------

    /**
     * Entrant accepts their invitation by picking a campsite and dates.
     *
     * @param reservation An INVITED or entrant-CANCELLED reservation.
     * @param campsiteId The campsite they picked.
     * @param start Start of their stay.
     * @param end End of their stay.
     * @param occupancy Capacity shown by the caller, retained for API compatibility.
     * The live campsite capacity read inside the transaction controls the booking.
     * @param onResult true if reserved, false if not allowed or the dates are full.
     */
    @Suppress("UNUSED_PARAMETER")
    fun reserve(
        reservation: Reservation,
        campsiteId: String,
        start: LocalDateTime,
        end: LocalDateTime,
        occupancy: Int,
        onResult: (Boolean) -> Unit
    ) = update(reservation.id, onResult) { tx, current ->
        if (!isDocumentId(campsiteId)) return@update false
        // Check the live campsite again on every retry, before staging any writes.
        val campsite = tx.get(db.collection("campsites").document(campsiteId))
        if (!campsite.exists()) return@update false
        val currentOccupancy = campsite.getLong("occupancy") ?: return@update false
        if (currentOccupancy !in 1L..Int.MAX_VALUE.toLong()) return@update false
        updateBooking(tx, current) { schedule ->
            current.reserve(schedule, campsiteId, start, end, currentOccupancy.toInt())
        }
    }

    /**
     * Entrant says no to their invitation.
     */
    fun decline(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation.id, onResult) { _, current ->
        current.decline()
    }

    /**
     * Entrant cancels a reservation before their stay starts.
     */
    fun cancel(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation.id, onResult) { tx, current ->
        current.status == ReservationStatus.RESERVED &&
            updateBooking(tx, current) { schedule -> current.cancel(schedule) }
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
    ) = update(reservation.id, onResult) { tx, current ->
        if (current.status == ReservationStatus.RESERVED) {
            updateBooking(tx, current) { schedule -> current.reject(schedule) }
        } else {
            current.reject()
        }
    }

    /** Owner/admin restores an invitation without reclaiming its old booking dates. */
    fun unreject(
        reservation: Reservation,
        onResult: (Boolean) -> Unit
    ) = update(reservation.id, onResult) { _, current ->
        current.unreject()
    }


    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Applies a change to the persisted reservation and writes it only if the action succeeds. */
    private fun update(
        reservationId: String,
        onResult: (Boolean) -> Unit,
        action: (Transaction, Reservation) -> Boolean
    ) {
        if (!isDocumentId(reservationId)) {
            onResult(false)
            return
        }

        val reservationDoc = reservationRef.document(reservationId)

        db.runTransaction { tx ->
            val current = tx.get(reservationDoc)
                .toObject(Reservation::class.java)
                ?.apply { id = reservationDoc.id }
                ?: return@runTransaction false

            if (!action(tx, current)) return@runTransaction false
            tx.set(reservationDoc, current)
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

    /** Reads the schedule named by the persisted reservation and stages its write on success. */
    private fun updateBooking(
        tx: Transaction,
        reservation: Reservation,
        change: (BookingSchedule<Lottery>) -> Boolean
    ): Boolean {
        if (!isDocumentId(reservation.lotteryId)) return false
        val scheduleDoc = scheduleRepository.scheduleDoc(reservation.lotteryId)
        val schedule = BookingScheduleRepository.fromSnapshot(tx.get(scheduleDoc)) ?: return false
        if (schedule.registration.id != reservation.lotteryId || !change(schedule)) return false
        tx.set(scheduleDoc, BookingScheduleRepository.toMap(schedule))
        return true
    }

    private fun isDocumentId(value: String): Boolean = value.isNotBlank() && '/' !in value

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
