package com.example.myapplication

import com.example.myapplication.backEnd.featureModels.AppNotification
import com.example.myapplication.backEnd.featureModels.buildResultNotifications
import org.junit.Assert.*
import org.junit.Test

class NotificationTests {

    @Test
    fun winnersGetWinAndOthersGetLose() {
        val result = buildResultNotifications("L1", "Camp", listOf("a", "b", "c"), listOf("b"))
        assertEquals("WIN", result.first { it.userId == "b" }.type)
        assertEquals("LOSE", result.first { it.userId == "a" }.type)
        assertEquals("LOSE", result.first { it.userId == "c" }.type)
        assertEquals(3, result.size)
    }

    @Test
    fun noEntrantGetsTwoNotifications() {
        val result = buildResultNotifications("L1", "Camp", listOf("a", "a", "b"), listOf("a"))
        assertEquals(result.size, result.map { it.userId }.distinct().size)
    }

    @Test
    fun idsAreDeterministic() {
        val first = buildResultNotifications("L1", "Camp", listOf("a"), listOf("a"))
        val second = buildResultNotifications("L1", "Camp", listOf("a"), listOf("a"))
        assertEquals("L1_a", first[0].id)
        assertEquals(first[0].id, second[0].id)
    }

    @Test
    fun emptyWaitlistProducesNothing() {
        assertTrue(buildResultNotifications("L1", "Camp", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun newNotificationStartsUnread() {
        assertFalse(AppNotification().read)
    }
}