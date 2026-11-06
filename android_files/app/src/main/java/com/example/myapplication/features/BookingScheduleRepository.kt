package com.example.myapplication.features

import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime

/**
 * Saves and loads booking schedules.
 *
 * Each lottery has one schedule document at bookingSchedules/<lotteryId>.
 * It holds the booking period and every booking made from that lottery:
 *
 *   lotteryId, ownerId, campsiteIds,
 *   bookingStart, bookingEnd,
 *   bookings: [ { entrantId, campsiteId, start, end }, ... ]
 *
 * Bookings are stored in one document (not one each) so ReservationRepository
 * can read and update them inside a transaction. That's what stops two
 * winners from both taking the last spot on the same day.
 *
 * Dates are stored as LocalDateTime.toString(), like LotteryRepository does.
 */
class BookingScheduleRepository {

    private val db = Firebase.firestore
    private val scheduleRef = db.collection("bookingSchedules")

    /**
     * The Firestore location of a lottery's booking schedule.
     * This only builds the location, it does not read or write.
     */
    fun scheduleDoc(lotteryId: String): DocumentReference =
        scheduleRef.document(lotteryId)

    /**
     * Saves a whole schedule, including its bookings.
     * Use this when the owner first sets the booking period.
     *
     * @param schedule The schedule to save.
     * @return Task that finishes when the save is done.
     */
    fun saveSchedule(schedule: BookingSchedule<Lottery>): Task<Void> =
        scheduleDoc(schedule.registration.id).set(toMap(schedule))

    /**
     * Changes only the booking period, keeping existing bookings.
     * Use this when the owner updates their lottery listing.
     *
     * @param lotteryId The lottery whose schedule to change.
     * @param start New start of the booking period.
     * @param end New end of the booking period.
     * @return Task that finishes when the save is done.
     */
    fun updateBookingPeriod(
        lotteryId: String,
        start: LocalDateTime,
        end: LocalDateTime
    ): Task<Void> =
        scheduleDoc(lotteryId).set(
            mapOf(
                "bookingStart" to start.toString(),
                "bookingEnd" to end.toString()
            ),
            SetOptions.merge()
        )

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
        if (lotteryId.isBlank()) {
            onResult(null)
            return
        }

        scheduleDoc(lotteryId).get()
            .addOnSuccessListener { doc -> onResult(fromSnapshot(doc)) }
            .addOnFailureListener { onResult(null) }
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
            "bookings" to schedule.getBookings().map { booking ->
                mapOf(
                    "entrantId" to booking.entrantId,
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
                val lottery = Lottery(
                    id = doc.getString("lotteryId") ?: "",
                    ownerId = doc.getString("ownerId") ?: "",
                    campsiteIds = (doc.get("campsiteIds") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                )

                val schedule = BookingSchedule(
                    registration = lottery,
                    bookingStart = LocalDateTime.parse(doc.getString("bookingStart")),
                    bookingEnd = LocalDateTime.parse(doc.getString("bookingEnd"))
                )

                (doc.get("bookings") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>()
                    ?.forEach { saved ->
                        schedule.restoreBooking(
                            schedule.Booking(
                                entrantId = saved["entrantId"] as String,
                                campsiteId = saved["campsiteId"] as String,
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
    }
}