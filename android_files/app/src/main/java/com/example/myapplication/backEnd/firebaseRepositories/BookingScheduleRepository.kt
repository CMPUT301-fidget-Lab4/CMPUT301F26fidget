package com.example.myapplication.backEnd.firebaseRepositories

import com.example.myapplication.backEnd.featureModels.BookingSchedule.Booking
import com.example.myapplication.backEnd.featureModels.BookingSchedule
import com.example.myapplication.backEnd.featureModels.Lottery
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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
                val lottery = Lottery(
                    id = doc.getString("lotteryId") ?: "",
                    ownerId = doc.getString("ownerId") ?: "",
                    campsiteIds = (doc.get("campsiteIds") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                )

                val sharedWindow = Booking(
                    startDate = LocalDateTime.parse(doc.getString("bookingStart")),
                    endDate = LocalDateTime.parse(doc.getString("bookingEnd"))
                )
                val schedule = BookingSchedule(
                    registration = lottery,
                    bookingStart = sharedWindow.startDate,
                    bookingEnd = sharedWindow.endDate,
                    campsitePeriods = (doc.get("campsitePeriods") as? Map<*, *>).orEmpty().entries.associate { (id, value) ->
                        val fields = value as Map<*, *>
                        val campsiteId = id as String
                        campsiteId to Booking(campsiteId = campsiteId,
                            startDate = LocalDateTime.parse(fields["start"] as String),
                            endDate = LocalDateTime.parse(fields["end"] as String))
                    }
                )

                require(schedule.campsitePeriods.keys.all { it in lottery.campsiteIds }) { "Unknown campsite booking period." }

                (doc.get("bookings") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>()
                    ?.forEach { saved ->
                        schedule.restoreBooking(
                            BookingSchedule.Booking(
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
