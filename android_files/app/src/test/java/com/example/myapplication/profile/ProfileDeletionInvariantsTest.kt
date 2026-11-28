package com.example.myapplication.profile

import com.example.myapplication.features.profile.ProfileDeletionPlan
import org.junit.Assert.*
import org.junit.Test

/** Cleanup outcome invariants, independent of Firestore or Compose. */
class ProfileDeletionInvariantsTest {
    @Test fun deletingUnrelatedUserDoesNotNormalizeLegacyOrCompleteSchedules() {
        val legacy = mapOf<String, Any?>("lotteryId" to "unrelated", "ownerId" to "someone", "campsiteIds" to listOf("camp"))
        val complete = legacy + mapOf("bookings" to emptyList<Any>(), "campsitePeriods" to emptyMap<String, Any>())
        val docs = mapOf("bookingSchedules/legacy" to legacy, "bookingSchedules/complete" to complete)
        val original = docs.mapValues { it.value.toMap() }
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        assertEquals(mapOf<String, Map<String, Any?>?>("users/deleted" to null), plan)
        assertEquals(original, docs)
    }

    @Test fun deletingCreatorScrubsTheirIdFromSurvivingCurrentAndHistoricalCopies() {
        val base = mapOf<String, Any?>("ownerId" to "survivor", "campsiteIds" to listOf("camp"), "isPrimary" to true,
            "waitlist" to emptyList<String>(), "winners" to emptyList<String>(), "createdById" to "deleted")
        val docs = mapOf("campsites/camp" to mapOf("ownerID" to "survivor"),
            "campsites/camp/lottery/current" to (base + ("id" to "current")),
            "campsites/camp/lottery/history_old" to (base + ("id" to "old")))
        val before = docs.mapValues { it.value.toMap() }
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        listOf("current", "history_old").forEach { copy ->
            val path = "campsites/camp/lottery/$copy"
            assertTrue("Creator cleanup must be explicitly planned for $path", plan.containsKey(path))
            val fields = plan.getValue(path)!!
            assertEquals("", fields["createdById"])
            assertEquals("survivor", (docs.getValue(path) + fields)["ownerId"])
            assertEquals(emptyList<String>(), (docs.getValue(path) + fields)["waitlist"])
        }
        assertEquals(before, docs)
    }

    @Test fun unrelatedCreatorAndCompletedReservationHistoryArePreserved() {
        val lottery = mapOf<String, Any?>("id" to "lot", "ownerId" to "survivor", "createdById" to "unrelated",
            "campsiteIds" to listOf("camp"), "isPrimary" to true, "waitlist" to listOf("other"), "winners" to emptyList<String>())
        val docs = linkedMapOf("campsites/camp" to mapOf<String, Any?>("ownerID" to "survivor"), "campsites/camp/lottery/current" to lottery)
        listOf("DECLINED", "CANCELLED", "REJECTED").forEach { status ->
            docs["reservations/$status"] = mapOf("entrantId" to "deleted", "ownerId" to "survivor", "lotteryId" to "lot", "status" to status,
                "cancellationReason" to "existing-reason", "updatedAt" to 10L)
        }
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        assertFalse(plan.containsKey("campsites/camp/lottery/current"))
        listOf("DECLINED", "CANCELLED", "REJECTED").forEach { status ->
            val changed = docs.getValue("reservations/$status") + plan.getValue("reservations/$status")!!
            assertEquals("", changed["entrantId"])
            assertEquals(status, changed["status"])
            assertEquals(if (status == "DECLINED") "existing-reason" else "PROFILE_DELETED", changed["cancellationReason"])
            assertEquals(10L, changed["updatedAt"])
        }
    }

    @Test fun invitationsAndReservationsAreCancelledAndOnlyRelatedCommunicationsRemoved() {
        val docs = linkedMapOf<String, Map<String, Any?>>()
        listOf("INVITED", "RESERVED").forEach { status -> docs["reservations/$status"] = mapOf("entrantId" to "deleted", "status" to status) }
        docs["messages/from"] = mapOf("senderId" to "deleted", "recipientId" to "other")
        docs["messages/to"] = mapOf("senderId" to "other", "recipientId" to "deleted")
        docs["messages/unrelated"] = mapOf("senderId" to "one", "recipientId" to "two")
        docs["notifications/related"] = mapOf("userId" to "deleted")
        docs["notifications/unrelated"] = mapOf("userId" to "other")
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        listOf("INVITED", "RESERVED").forEach { status ->
            assertEquals(mapOf("status" to "CANCELLED", "cancellationReason" to "PROFILE_DELETED", "updatedAt" to 100L, "entrantId" to ""), plan["reservations/$status"])
        }
        listOf("messages/from", "messages/to", "notifications/related").forEach { assertTrue(plan.containsKey(it)); assertNull(plan[it]) }
        assertFalse(plan.containsKey("messages/unrelated"))
        assertFalse(plan.containsKey("notifications/unrelated"))
    }
    @Test fun unrelatedSparseAndOrphanLotteryCopiesAreNotNormalizedOrDeleted() {
        val docs = mapOf(
            "campsites/camp" to mapOf<String, Any?>("ownerID" to "survivor"),
            "campsites/camp/lottery/current" to mapOf("id" to "sparse", "ownerId" to "survivor", "campsiteIds" to listOf("camp")),
            "campsites/camp/lottery/history_orphan" to mapOf("id" to "orphan", "ownerId" to "survivor", "campsiteIds" to emptyList<String>()))
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        assertEquals(mapOf<String, Map<String, Any?>?>("users/deleted" to null), plan)
    }

    @Test fun removedCampsiteRevokesCancelledAndRejectedEntitlementsButPreservesHistory() {
        val shared = mapOf<String, Any?>("id" to "lot", "ownerId" to "deleted", "createdById" to "someone",
            "campsiteIds" to listOf("gone", "kept"), "waitlist" to emptyList<String>(), "winners" to listOf("entrant"))
        val docs = linkedMapOf<String, Map<String, Any?>>(
            "campsites/gone" to mapOf("ownerID" to "deleted"),
            "campsites/kept" to mapOf("ownerID" to "survivor"),
            "campsites/gone/lottery/current" to (shared + ("isPrimary" to true)),
            "campsites/kept/lottery/current" to (shared + ("isPrimary" to false)))
        listOf("CANCELLED", "REJECTED").forEach { status -> docs["reservations/$status"] = mapOf(
            "lotteryId" to "lot", "ownerId" to "deleted", "entrantId" to "entrant", "campsiteId" to "gone", "status" to status,
            "bookingStart" to "2035-01-01T00:00", "bookingEnd" to "2035-01-02T00:00", "updatedAt" to 10L) }
        docs["reservations/unaffected"] = mapOf("lotteryId" to "lot", "ownerId" to "deleted", "entrantId" to "other", "campsiteId" to "kept", "status" to "INVITED")
        docs["reservations/unrelated-terminal"] = mapOf("lotteryId" to "unrelated", "ownerId" to "elsewhere", "entrantId" to "other", "campsiteId" to "unrelated", "status" to "REJECTED", "cancellationReason" to "existing")
        val plan = ProfileDeletionPlan.build("deleted", docs, 100L)
        listOf("CANCELLED", "REJECTED").forEach { status ->
            val changed = docs.getValue("reservations/$status") + plan.getValue("reservations/$status")!!
            assertEquals(status, changed["status"])
            assertEquals(10L, changed["updatedAt"])
            assertEquals("2035-01-01T00:00", changed["bookingStart"])
            assertEquals("2035-01-02T00:00", changed["bookingEnd"])
            assertEquals("PROFILE_DELETED", changed["cancellationReason"])
            val model = com.example.myapplication.features.booking.Reservation(
                status = com.example.myapplication.features.booking.ReservationStatus.valueOf(status),
                cancellationReason = changed["cancellationReason"] as String)
            assertFalse(model.canChooseDates())
            assertFalse(model.unreject())
        }
        val unaffected = docs.getValue("reservations/unaffected") + plan.getValue("reservations/unaffected")!!
        assertEquals("INVITED", unaffected["status"])
        assertFalse(unaffected.containsKey("cancellationReason"))
        assertEquals("survivor", unaffected["ownerId"])
        assertFalse(plan.containsKey("reservations/unrelated-terminal"))
    }
}
