package com.example.myapplication.profile

import com.example.myapplication.features.profile.ProfileDeletionPlan
import org.junit.Assert.*
import org.junit.Test

class ProfileDeletionPlanTest {
    @Test fun discoveryIncludesMembersAndUnbookedInvitationsWhenACampsiteIsRemoved() {
        val input = fixture() + (
            "reservations/invite" to mapOf("lotteryId" to "lot", "ownerId" to "survivor", "status" to "INVITED")
        )
        val discovery = ProfileDeletionPlan.discover("owner", input)
        assertTrue("users/entrant" in discovery.paths)
        assertTrue("reservations/invite" in discovery.paths)
        assertTrue("reservations/lot_entrant" in discovery.paths)

        val path = "campsites/b/lottery/current"
        val newJoin = input + (path to (input.getValue(path) + ("waitlist" to listOf("entrant", "other", "new"))))
        assertTrue(ProfileDeletionPlan.structuralRelationshipsMatch(discovery.structuralLotteryPaths, input, input))
        assertFalse(ProfileDeletionPlan.structuralRelationshipsMatch(discovery.structuralLotteryPaths, input, newJoin))
    }

    private fun lottery(primary: Boolean) = mapOf<String, Any?>("id" to "lot", "ownerId" to "owner",
        "campsiteIds" to listOf("a", "b"), "waitlist" to listOf("entrant", "other"),
        "winners" to listOf("entrant"), "isPrimary" to primary)
    private fun fixture() = mapOf(
        "users/owner" to mapOf("name" to "Owner"),
        "users/entrant" to mapOf("name" to "Entrant"),
        "users/other" to mapOf("joinedLotteries" to listOf(mapOf("lotteryId" to "lot", "campsiteName" to "a"))),
        "campsites/a" to mapOf("ownerID" to "owner"),
        "campsites/b" to mapOf("ownerID" to "survivor"),
        "campsites/a/lottery/current" to lottery(true),
        "campsites/b/lottery/current" to lottery(false),
        "campsites/a/lottery/history_old" to (lottery(true) + mapOf("id" to "old", "campsiteIds" to listOf("a"))),
        "bookingSchedules/lot" to mapOf("lotteryId" to "lot", "ownerId" to "owner", "campsiteIds" to listOf("a", "b"),
            "campsitePeriods" to mapOf("a" to mapOf("start" to "date")),
            "bookings" to listOf(mapOf("entrantId" to "entrant", "campsiteId" to "a"), mapOf("entrantId" to "other", "campsiteId" to "b"))),
        "reservations/r" to mapOf("lotteryId" to "lot", "ownerId" to "owner", "entrantId" to "entrant", "campsiteId" to "a", "status" to "RESERVED"),
        "notifications/n" to mapOf("userId" to "entrant"),
        "messages/m" to mapOf("senderId" to "entrant", "recipientId" to "owner")
    )
    @Test fun ownerDeletionRemovesSubcollectionsAndPromotesSurvivingPrimary() {
        val plan = ProfileDeletionPlan.build("owner", fixture(), 123)
        assertTrue(plan.containsKey("users/owner"))
        assertNull(plan["users/owner"])
        assertTrue("Owned campsite must have an explicit deletion entry", plan.containsKey("campsites/a"))
        assertNull(plan["campsites/a"])
        assertTrue(plan.containsKey("campsites/a/lottery/history_old"))
        assertTrue(plan.containsKey("campsites/a/lottery/current"))
        assertNull(plan["campsites/a/lottery/current"])
        val surviving = plan.getValue("campsites/b/lottery/current")!!
        assertEquals(true, surviving["isPrimary"])
        assertEquals(listOf("b"), surviving["campsiteIds"])
        assertEquals("survivor", surviving["ownerId"])
        assertEquals("CANCELLED", plan.getValue("reservations/r")!!["status"])
        assertEquals("PROFILE_DELETED", plan.getValue("reservations/r")!!["cancellationReason"])
        val schedule = plan.getValue("bookingSchedules/lot")!!
        assertEquals(listOf("b"), schedule["campsiteIds"])
        assertEquals(listOf(mapOf("entrantId" to "other", "campsiteId" to "b")), schedule["bookings"])
    }
    @Test fun entrantDeletionRemovesMembershipAcrossCopiesAndReleasesBooking() {
        val plan = ProfileDeletionPlan.build("entrant", fixture(), 123)
        listOf("a", "b").forEach {
            val fields = plan.getValue("campsites/$it/lottery/current")!!
            assertEquals(listOf("other"), fields["waitlist"])
            assertEquals(emptyList<String>(), fields["winners"])
        }
        assertEquals("", plan.getValue("reservations/r")!!["entrantId"])
        assertEquals(123L, plan.getValue("reservations/r")!!["updatedAt"])
        assertTrue(plan.containsKey("messages/m"))
        assertTrue(plan.containsKey("notifications/n"))
        assertFalse(plan.containsKey("campsites/a"))
    }
    @Test fun deletionIsIdempotentAndPreservesUnrelatedData() {
        val docs = fixture()
        val plan = ProfileDeletionPlan.build("owner", docs, 123)
        val after = docs.toMutableMap()
        plan.forEach { (path, fields) -> if (fields == null) after.remove(path) else after[path] = after.getValue(path) + fields }
        val second = ProfileDeletionPlan.build("owner", after, 456)
        assertEquals(mapOf<String, Map<String, Any?>?>("users/owner" to null), second)
        assertEquals(docs["users/entrant"], after["users/entrant"])
    }

    @Test fun deletingAnEntrantRemovesTheirPendingResultAndKeepsOtherRecipients() {
        val docs = fixture() + mapOf(
            "lotteryDrawResults/lot" to mapOf("campsiteName" to "a", "remainingEntrantIds" to listOf("entrant", "other"),
                "winnerIds" to listOf("entrant", "other")),
            "lotteryDrawResults/last" to mapOf("campsiteName" to "b", "remainingEntrantIds" to listOf("entrant"),
                "winnerIds" to emptyList<String>()),
            "lotteryDrawResults/unrelated" to mapOf("campsiteName" to "c", "remainingEntrantIds" to listOf("other"),
                "winnerIds" to listOf("other"))
        )

        val plan = ProfileDeletionPlan.build("entrant", docs, 123)

        assertEquals(mapOf("remainingEntrantIds" to listOf("other"), "winnerIds" to listOf("other")),
            plan.getValue("lotteryDrawResults/lot"))
        assertNull(plan.getValue("lotteryDrawResults/last"))
        assertFalse(plan.containsKey("lotteryDrawResults/unrelated"))
    }

    @Test fun lastCampsiteDeletionRetainsScheduleButClearsAvailability() {
        val docs = fixture().toMutableMap()
        docs["campsites/b"] = mapOf("ownerID" to "owner")
        val plan = ProfileDeletionPlan.build("owner", docs, 123)
        assertTrue(plan.containsKey("campsites/b/lottery/current"))
        assertNull(plan["campsites/b/lottery/current"])
        val schedule = plan.getValue("bookingSchedules/lot")!!
        assertEquals(emptyList<String>(), schedule["campsiteIds"])
        assertEquals(emptyList<Any>(), schedule["bookings"])
        assertEquals(emptyList<Any>(), plan.getValue("users/other")!!["joinedLotteries"])
    }
}
