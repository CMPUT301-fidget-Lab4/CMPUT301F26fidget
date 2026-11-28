package com.example.myapplication.booking

import com.example.myapplication.features.booking.BookingSchedule
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class BookingIntegrityTest {
    private val start = LocalDateTime.of(2099, 7, 1, 0, 0)
    private val end = start.plusDays(1)

    @Test
    fun cancellationCannotReleaseAnotherLotterysBooking() {
        val db = mock(FirebaseFirestore::class.java)
        val reservationCollection = mock(CollectionReference::class.java)
        val scheduleCollection = mock(CollectionReference::class.java)
        val reservationRef = mock(DocumentReference::class.java)
        val rightScheduleRef = mock(DocumentReference::class.java)
        val wrongScheduleRef = mock(DocumentReference::class.java)
        val reservationSnapshot = mock(DocumentSnapshot::class.java)
        val transaction = mock(Transaction::class.java)
        val persisted = Reservation(
            id = "reservation",
            lotteryId = "right-lottery",
            entrantId = "entrant",
            campsiteId = "camp",
            bookingStart = start.toString(),
            bookingEnd = end.toString(),
            status = ReservationStatus.RESERVED
        )
        `when`(db.collection("reservations")).thenReturn(reservationCollection)
        `when`(db.collection("bookingSchedules")).thenReturn(scheduleCollection)
        `when`(reservationCollection.document("reservation")).thenReturn(reservationRef)
        `when`(scheduleCollection.document("right-lottery")).thenReturn(rightScheduleRef)
        `when`(scheduleCollection.document("wrong-lottery")).thenReturn(wrongScheduleRef)
        `when`(reservationRef.id).thenReturn("reservation")
        `when`(transaction.get(reservationRef)).thenReturn(reservationSnapshot)
        `when`(reservationSnapshot.toObject(Reservation::class.java)).thenAnswer { persisted.copy() }
        val otherStart = start.plusDays(8)
        val rightScheduleSnapshot = scheduleSnapshot("right-lottery", listOf(booking()))
        val wrongScheduleSnapshot = scheduleSnapshot("wrong-lottery", listOf(booking(otherStart)), windowStart = otherStart)
        `when`(transaction.get(rightScheduleRef)).thenReturn(rightScheduleSnapshot)
        `when`(transaction.get(wrongScheduleRef)).thenReturn(wrongScheduleSnapshot)

        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<Boolean>
        var transactionResult: Boolean? = null
        doAnswer { call ->
            transactionResult = call.getArgument<Transaction.Function<Boolean>>(0).apply(transaction)
            task
        }.`when`(db).runTransaction<Boolean>(any())
        doAnswer { call ->
            call.getArgument<OnSuccessListener<Boolean>>(0).onSuccess(transactionResult)
            task
        }.`when`(task).addOnSuccessListener(any())

        val repository = ReservationRepository(BookingScheduleRepository(db), db)
        var result: Boolean? = null
        repository.cancel(persisted.copy(lotteryId = "wrong-lottery")) { result = it }

        val writes = mockingDetails(transaction).invocations.filter { it.method.name == "set" }
        assertEquals("A cancellation must never rewrite an unrelated lottery's schedule", 0,
            writes.count { it.arguments[0] === wrongScheduleRef })
        val reservationWrites = writes.filter { it.arguments[0] === reservationRef }
        val rightScheduleWrites = writes.filter { it.arguments[0] === rightScheduleRef }
        assertEquals(true, result)
        val writtenReservation = reservationWrites.single().arguments[1] as Reservation
        val writtenSchedule = rightScheduleWrites.single().arguments[1] as Map<*, *>
        assertEquals(ReservationStatus.CANCELLED, writtenReservation.status)
        assertEquals(emptyList<Any>(), writtenSchedule["bookings"])
    }

    @Test
    fun malformedBookingEntryMakesScheduleUnreadable() {
        val snapshot = scheduleSnapshot("lottery", listOf(booking(), "invalid booking row"))

        assertNull("A corrupt booking row must not silently reduce the counted occupancy",
            BookingScheduleRepository.fromSnapshot(snapshot))
    }

    @Test
    fun omittedLegacyBookingsAreEmptyButNullOrMismatchedIdentityIsInvalid() {
        val legacy = scheduleSnapshot("lottery", bookings = null)
        assertEquals(emptyList<BookingSchedule.Booking>(), BookingScheduleRepository.fromSnapshot(legacy)?.getBookings())
        `when`(legacy.contains("bookings")).thenReturn(true)
        assertNull(BookingScheduleRepository.fromSnapshot(legacy))

        val mismatched = scheduleSnapshot("lottery", listOf(booking()))
        `when`(mismatched.id).thenReturn("wrong-lottery")
        assertNull(BookingScheduleRepository.fromSnapshot(mismatched))
    }

    @Test
    fun fullNightBetweenAvailableEndpointsBlocksTheWholeStay() {
        val campsite = "camp"
        val schedule = BookingSchedule(Lottery(id = "lottery"), start, start.plusDays(4))
        val middle = start.plusDays(1)
        val last = start.plusDays(2)
        assertTrue(schedule.addBooking(BookingSchedule.Booking("other", campsite, middle, last), 1))

        assertTrue(start.toLocalDate() in schedule.availableDates(campsite, 1))
        assertTrue(last.toLocalDate() in schedule.availableDates(campsite, 1))
        assertFalse(schedule.isStayAvailable(campsite, start, last.plusDays(1), 1))
        assertTrue(schedule.isStayAvailable(campsite, last, last.plusDays(1), 1))
    }

    private fun booking(bookingStart: LocalDateTime = start): Map<String, String> = mapOf(
        "entrantId" to "entrant",
        "campsiteId" to "camp",
        "start" to bookingStart.toString(),
        "end" to bookingStart.plusDays(1).toString()
    )

    private fun scheduleSnapshot(
        lotteryId: String,
        bookings: List<Any>?,
        windowStart: LocalDateTime = start
    ): DocumentSnapshot =
        mock(DocumentSnapshot::class.java).also { snapshot ->
            `when`(snapshot.exists()).thenReturn(true)
            `when`(snapshot.id).thenReturn(lotteryId)
            `when`(snapshot.getString("lotteryId")).thenReturn(lotteryId)
            `when`(snapshot.getString("ownerId")).thenReturn("owner")
            `when`(snapshot.get("campsiteIds")).thenReturn(listOf("camp"))
            `when`(snapshot.getString("bookingStart")).thenReturn(windowStart.toString())
            `when`(snapshot.getString("bookingEnd")).thenReturn(windowStart.plusDays(7).toString())
            `when`(snapshot.get("bookings")).thenReturn(bookings)
        }
}
