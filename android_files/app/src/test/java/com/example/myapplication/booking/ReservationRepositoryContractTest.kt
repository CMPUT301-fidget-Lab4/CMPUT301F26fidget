package com.example.myapplication.booking

import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import java.time.LocalDateTime

/** Actual reserve transaction; external snapshots and final commit delivery are controlled locally. */
class ReservationRepositoryContractTest {
    @Test fun staleCallerCapacityCannotOverbookAnAuthoritativelyFullCampsite() {
        val f = Fixture()
        f.reserve(callerCapacity = 100)
        assertEquals(false, f.result)
        f.assertNoWrites()
        assertEquals(ReservationStatus.INVITED, f.persisted.status)
    }

    @Test fun fullCampsiteStillAllowsDatesWithoutOverlap() {
        val f = Fixture()
        f.reserve(callerCapacity = 100, start = f.start.plusDays(3), end = f.start.plusDays(4))
        assertEquals(true, f.result)
        val saved = f.savedReservation()!!
        assertEquals(ReservationStatus.RESERVED, saved.status)
        assertEquals(f.start.plusDays(3).toString(), saved.bookingStart)
        assertEquals(f.start.plusDays(4).toString(), saved.bookingEnd)
        val bookings = f.savedSchedule()!!["bookings"] as List<*>
        assertEquals(2, bookings.size)
        assertTrue(bookings.contains(mapOf("entrantId" to "entrant", "campsiteId" to "camp", "start" to saved.bookingStart, "end" to saved.bookingEnd)))
    }

    @Test fun deletedCampsiteCannotBeBookedFromRetainedSchedule() {
        val f = Fixture(campExists = false)
        f.reserve(callerCapacity = 100, start = f.start.plusDays(3), end = f.start.plusDays(4))
        assertEquals(false, f.result)
        f.assertNoWrites()
        assertEquals(ReservationStatus.INVITED, f.persisted.status)
    }

    @Test fun latestDeletedProfileReasonBlocksStaleInvitationWithoutAnyWrites() {
        val f = Fixture(reason = "PROFILE_DELETED")
        f.reserve(callerCapacity = 100, start = f.start.plusDays(3), end = f.start.plusDays(4))
        assertEquals(false, f.result)
        f.assertNoWrites()
        assertEquals("PROFILE_DELETED", f.persisted.cancellationReason)
    }

    @Test fun failedCommitReportsFailureWithoutMutatingCallerObject() {
        val f = Fixture(commitFails = true)
        val before = f.stale.copy()
        f.reserve(callerCapacity = 100, start = f.start.plusDays(3), end = f.start.plusDays(4))
        assertEquals(false, f.result)
        assertEquals(before, f.stale)
        assertNotNull(f.savedReservation()) // A requested write is not a committed record.
        assertEquals(ReservationStatus.INVITED, f.persisted.status)
    }

    @Test fun declineAndUnrejectOnlyReadAndWriteTheReservation() {
        val decline = Fixture()
        decline.decline()
        assertEquals(true, decline.result)
        assertEquals(ReservationStatus.DECLINED, decline.savedReservation()?.status)
        decline.assertScheduleUntouched()

        val reject = Fixture()
        reject.reject()
        assertEquals(true, reject.result)
        assertEquals(ReservationStatus.REJECTED, reject.savedReservation()?.status)
        reject.assertScheduleUntouched()

        val unreject = Fixture(status = ReservationStatus.REJECTED)
        unreject.unreject()
        assertEquals(true, unreject.result)
        assertEquals(ReservationStatus.INVITED, unreject.savedReservation()?.status)
        unreject.assertScheduleUntouched()
    }

    @Test fun confirmedStayWithoutItsExactBookingCannotBeCancelledOrRejected() {
        val cancel = Fixture(status = ReservationStatus.RESERVED)
        cancel.cancel()
        assertEquals(false, cancel.result)
        cancel.assertNoWrites()

        val reject = Fixture(status = ReservationStatus.RESERVED)
        reject.reject()
        assertEquals(false, reject.result)
        reject.assertNoWrites()
    }

    @Test fun malformedBookingRowsBlockNewReservationsAndNeverRewriteTheSchedule() {
        val f = Fixture(malformedBookings = true)
        f.reserve(callerCapacity = 100, start = f.start.plusDays(3), end = f.start.plusDays(4))
        assertEquals(false, f.result)
        f.assertNoWrites()
    }

    private class Fixture(
        campExists: Boolean = true,
        reason: String = "",
        private val commitFails: Boolean = false,
        status: ReservationStatus = ReservationStatus.INVITED,
        malformedBookings: Boolean = false
    ) {
        val start = LocalDateTime.of(2035, 1, 5, 0, 0)
        val persisted = Reservation(
            id = "r", lotteryId = "lot", entrantId = "entrant", status = status, cancellationReason = reason,
            campsiteId = "camp", bookingStart = start.toString(), bookingEnd = start.plusDays(2).toString()
        )
        val stale = persisted.copy(cancellationReason = "")
        var result: Boolean? = null
        private val db = mock(FirebaseFirestore::class.java)
        private val tx = mock(Transaction::class.java)
        private val reservationRef = mock(DocumentReference::class.java)
        private val scheduleRef = mock(DocumentReference::class.java)
        private val repo: ReservationRepository
        @Suppress("UNCHECKED_CAST") private val task = mock(Task::class.java) as Task<Boolean>

        init {
            val reservations = mock(CollectionReference::class.java)
            val schedules = mock(CollectionReference::class.java)
            val campsites = mock(CollectionReference::class.java)
            val campRef = mock(DocumentReference::class.java)
            val camp = mock(DocumentSnapshot::class.java)
            val reservation = mock(DocumentSnapshot::class.java)
            val schedule = mock(DocumentSnapshot::class.java)
            `when`(db.collection("reservations")).thenReturn(reservations)
            `when`(db.collection("bookingSchedules")).thenReturn(schedules)
            `when`(db.collection("campsites")).thenReturn(campsites)
            `when`(reservations.document("r")).thenReturn(reservationRef)
            `when`(reservationRef.id).thenReturn("r")
            `when`(schedules.document("lot")).thenReturn(scheduleRef)
            `when`(campsites.document("camp")).thenReturn(campRef)
            `when`(tx.get(campRef)).thenReturn(camp)
            `when`(camp.exists()).thenReturn(campExists)
            `when`(camp.getLong("occupancy")).thenReturn(1L)
            `when`(tx.get(reservationRef)).thenReturn(reservation)
            `when`(reservation.toObject(Reservation::class.java)).thenAnswer { persisted.copy() }
            `when`(tx.get(scheduleRef)).thenReturn(schedule)
            `when`(schedule.exists()).thenReturn(true)
            `when`(schedule.id).thenReturn("lot")
            `when`(schedule.getString("lotteryId")).thenReturn("lot")
            `when`(schedule.getString("ownerId")).thenReturn("owner")
            `when`(schedule.getString("bookingStart")).thenReturn(start.toString())
            `when`(schedule.getString("bookingEnd")).thenReturn(start.plusDays(8).toString())
            `when`(schedule.get("campsiteIds")).thenReturn(listOf("camp"))
            val storedBookings = listOf(
                mapOf("entrantId" to "other", "campsiteId" to "camp", "start" to start.toString(), "end" to start.plusDays(2).toString())
            )
            `when`(schedule.get("bookings")).thenReturn(
                if (malformedBookings) storedBookings + "malformed booking" else storedBookings
            )
            var computed = false
            var bodyFailure: Exception? = null
            doAnswer { call ->
                try { computed = call.getArgument<Transaction.Function<Boolean>>(0).apply(tx) == true }
                catch(e: Exception) { bodyFailure = e }
                task
            }.`when`(db).runTransaction<Boolean>(any())
            doAnswer { call ->
                if (!commitFails && bodyFailure == null) call.getArgument<OnSuccessListener<Boolean>>(0).onSuccess(computed)
                task
            }.`when`(task).addOnSuccessListener(any())
            doAnswer { call ->
                val error = bodyFailure ?: if (commitFails) IllegalStateException("simulated commit failure") else null
                if (error != null) call.getArgument<OnFailureListener>(0).onFailure(error)
                task
            }.`when`(task).addOnFailureListener(any())
            repo = ReservationRepository(BookingScheduleRepository(db), db)
        }
        fun reserve(callerCapacity: Int, start: LocalDateTime = this.start, end: LocalDateTime = this.start.plusDays(1)) {
            repo.reserve(stale, "camp", start, end, callerCapacity) { result = it }
        }
        fun cancel() = repo.cancel(stale) { result = it }
        fun reject() = repo.reject(stale) { result = it }
        fun decline() = repo.decline(stale) { result = it }
        fun unreject() = repo.unreject(stale) { result = it }
        fun savedReservation(): Reservation? = mockingDetails(tx).invocations.firstOrNull { it.method.name == "set" && it.arguments[0] === reservationRef }?.arguments?.get(1) as? Reservation
        @Suppress("UNCHECKED_CAST") fun savedSchedule(): Map<String, Any>? = mockingDetails(tx).invocations.firstOrNull { it.method.name == "set" && it.arguments[0] === scheduleRef }?.arguments?.get(1) as? Map<String, Any>
        fun assertNoWrites() { verify(tx, never()).set(any(DocumentReference::class.java), any<Any>()) }
        fun assertScheduleUntouched() {
            verify(tx, never()).get(scheduleRef)
            assertNull(savedSchedule())
        }
    }
}
