package com.example.myapplication.data.firebase

import com.example.myapplication.features.booking.BookingSchedule.Booking
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.booking.BookingSchedule
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime

/**
 * Saves and loads booking schedules.
 *
 * Each lottery has one schedule document at bookingSchedules/<lotteryId>.
 * It holds the booking period and every booking made from that lottery:
 *
 *   lotteryId, ownerId, campsiteIds,
 *   bookingStart, bookingEnd, campsitePeriods: { campsiteId: { start, end } },
 *   bookings: [ { entrantId, campsiteId, start, end }, ... ]
 *
 * Bookings are stored in one document (not one each) so ReservationRepository
 * can read and update them inside a transaction. That's what stops two
 * winners from both taking the last spot on the same day.
 *
 * Dates are stored as LocalDateTime.toString(), like LotteryRepository does.
 */
class BookingScheduleRepository(private val db: FirebaseFirestore = Firebase.firestore) {
    private val scheduleRef = db.collection("bookingSchedules")

    @ConsistentCopyVisibility
    data class Catalog internal constructor(
        val schedules: List<BookingSchedule<Lottery>>? = null,
        val fromCache: Boolean = false,
        val error: Exception? = null,
        val checking: Boolean = schedules == null && error == null
    ) {
        val byCampsite = schedules
            ?.flatMap { schedule -> schedule.registration.campsiteIds.map { id -> id to schedule } }
            ?.groupBy({ it.first }, { it.second })
    }

    /**
     * The Firestore location of a lottery's booking schedule.
     * This only builds the location, it does not read or write.
     */
    fun scheduleDoc(lotteryId: String): DocumentReference =
        scheduleRef.document(lotteryId)

    /**
     * Loads a lottery's booking schedule.
     *
     * @param lotteryId The lottery whose schedule to load.
     * @param onResult The schedule, or null if it doesn't exist or can't be read.
     */
    fun getSchedule(
        lotteryId: String,
        onResult: (BookingSchedule<Lottery>?) -> Unit
    ) {
        if (!isDocumentId(lotteryId)) {
            onResult(null)
            return
        }

        scheduleDoc(lotteryId).get()
            .addOnSuccessListener { doc -> onResult(fromSnapshot(doc)) }
            .addOnFailureListener { onResult(null) }
    }

    /** Reports failures separately so consumers can preserve the latest data and label it as stale. */
    internal fun observeSchedules(
        onError: (Exception) -> Unit,
        onChange: (Catalog) -> Unit
    ): ListenerRegistration =
        scheduleRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) {
                onError(error ?: IllegalStateException("No booking schedule snapshot was returned."))
                return@addSnapshotListener
            }
            val catalog = try {
                Catalog(
                    schedules = snapshot.documents.map { document ->
                        checkNotNull(fromSnapshot(document)) { "Booking schedule ${document.id} could not be read." }
                    },
                    fromCache = snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()
                )
            } catch (error: Exception) {
                onError(error)
                return@addSnapshotListener
            }
            onChange(catalog)
        }

    companion object {

        /**
         * Turns a schedule into the data saved in Firestore.
         */
        fun toMap(schedule: BookingSchedule<Lottery>): Map<String, Any> = mapOf(
            "lotteryId" to schedule.registration.id,
            "ownerId" to schedule.registration.ownerId,
            "campsiteIds" to schedule.registration.campsiteIds,
            "bookingStart" to schedule.bookingStart.toString(),
            "bookingEnd" to schedule.bookingEnd.toString(),
            "campsitePeriods" to schedule.campsitePeriods.mapValues { (_, period) ->
                mapOf("start" to period.startDate.toString(), "end" to period.endDate.toString())
            },
            "bookings" to schedule.getBookings().map { booking ->
                mapOf(
                    "entrantId" to checkNotNull(booking.entrantId),
                    "campsiteId" to booking.campsiteId,
                    "start" to booking.startDate.toString(),
                    "end" to booking.endDate.toString()
                )
            }
        )

        /**
         * Rebuilds a schedule from a Firestore document.
         *
         * The Lottery inside only has its id, owner and campsites filled in,
         * which is everything reserving needs. Load the full lottery with
         * LotteryRepository if you need the waitlist or winners.
         *
         * @return The schedule, or null if the document is missing or broken.
         */
        fun fromSnapshot(doc: DocumentSnapshot): BookingSchedule<Lottery>? {
            if (!doc.exists()) {
                return null
            }

            return try {
                val lotteryId = requireNotNull(doc.getString("lotteryId"))
                require(isDocumentId(lotteryId) && lotteryId == doc.id) { "Invalid booking schedule identity." }
                val storedCampsiteIds = doc.optionalField("campsiteIds") as List<*>?
                val campsiteIds = storedCampsiteIds.orEmpty()
                    .map { id -> (id as String).also { require(isDocumentId(it)) } }
                require(campsiteIds.distinct() == campsiteIds) { "Duplicate campsite in booking schedule." }
                val lottery = Lottery(
                    id = lotteryId,
                    ownerId = doc.getString("ownerId") ?: "",
                    campsiteIds = campsiteIds
                )

                val sharedWindow = Booking(
                    startDate = LocalDateTime.parse(doc.getString("bookingStart")),
                    endDate = LocalDateTime.parse(doc.getString("bookingEnd"))
                )
                val schedule = BookingSchedule(
                    registration = lottery,
                    bookingStart = sharedWindow.startDate,
                    bookingEnd = sharedWindow.endDate,
                    campsitePeriods = (doc.optionalField("campsitePeriods") as Map<*, *>?).orEmpty().entries.associate { (id, value) ->
                        val fields = value as Map<*, *>
                        val campsiteId = id as String
                        campsiteId to Booking(campsiteId = campsiteId,
                            startDate = LocalDateTime.parse(fields["start"] as String),
                            endDate = LocalDateTime.parse(fields["end"] as String))
                    }
                )

                require(schedule.campsitePeriods.keys.all { it in lottery.campsiteIds }) { "Unknown campsite booking period." }

                val recordedEntrants = mutableSetOf<String>()
                (doc.optionalField("bookings") as List<*>?).orEmpty().forEach { row ->
                    val saved = row as Map<*, *>
                    val entrantId = saved["entrantId"] as String
                    val campsiteId = saved["campsiteId"] as String
                    require(isDocumentId(entrantId) && recordedEntrants.add(entrantId)) {
                        "Invalid or duplicate booking entrant."
                    }
                    // Legacy schedules may omit the campsite list. Preserve the booking rows in those documents.
                    require(isDocumentId(campsiteId) && (storedCampsiteIds == null || campsiteId in campsiteIds)) {
                        "Unknown campsite in booking."
                    }
                    schedule.restoreBooking(
                        Booking(
                            entrantId = entrantId,
                            campsiteId = campsiteId,
                            startDate = LocalDateTime.parse(saved["start"] as String),
                            endDate = LocalDateTime.parse(saved["end"] as String)
                        )
                    )
                }

                schedule
            } catch (e: Exception) {
                null
            }
        }

        private fun isDocumentId(value: String): Boolean = value.isNotBlank() && '/' !in value

        /** Omitted legacy fields default to empty. An explicitly stored null is invalid. */
        private fun DocumentSnapshot.optionalField(field: String): Any? =
            get(field) ?: if (contains(field)) error("Null booking schedule field: $field") else null
    }
}
