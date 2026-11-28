package com.example.myapplication.profile

import com.example.myapplication.data.firebase.UserRepository
import com.example.myapplication.features.profile.JoinedLottery
import com.example.myapplication.features.profile.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ProfileUpdateTest {
    @Test
    fun `editing contact details preserves saved lottery membership and campsite links`() {
        val before = User(
            userType = "admin", name = "Before", email = "before@example.com", phoneNumber = "1234",
            deviceID = "profile", campsiteIds = listOf("pine"),
            joinedLotteries = listOf(JoinedLottery("weekend", "cedar"))
        )
        // ProfileScreen passes a fresh object containing exactly these five fields.
        val afterForm = User("admin", "After", "after@example.com", "5678", "profile", lotteryAlertsEnabled = false)
        var saved = before
        val firestore = mock(FirebaseFirestore::class.java)
        val profiles = mock(CollectionReference::class.java)
        val profile = mock(DocumentReference::class.java)
        @Suppress("UNCHECKED_CAST")
        val write = mock(Task::class.java) as Task<Void>
        `when`(firestore.collection("users")).thenReturn(profiles)
        `when`(profiles.document("profile")).thenReturn(profile)
        val listener = mock(ListenerRegistration::class.java)
        `when`(profiles.addSnapshotListener(eq(MetadataChanges.INCLUDE), any<EventListener<QuerySnapshot>>()))
            .thenReturn(listener)
        doAnswer {
            saved = it.getArgument(0)
            write
        }.`when`(profile).set(any(User::class.java))
        doAnswer {
            val fields = it.getArgument<Map<String, Any?>>(0)
            saved = saved.copy(
                name = fields["name"] as? String ?: saved.name,
                email = fields["email"] as? String ?: saved.email,
                phoneNumber = fields["phoneNumber"] as? String ?: saved.phoneNumber,
                lotteryAlertsEnabled = fields["lotteryAlertsEnabled"] as? Boolean ?: saved.lotteryAlertsEnabled,
                campsiteIds = if ("campsiteIds" in fields) {
                    (fields["campsiteIds"] as? List<*>).orEmpty().filterIsInstance<String>()
                } else saved.campsiteIds,
                joinedLotteries = if ("joinedLotteries" in fields) {
                    (fields["joinedLotteries"] as? List<*>).orEmpty().filterIsInstance<JoinedLottery>()
                } else saved.joinedLotteries
            )
            write
        }.`when`(profile).update(anyMap<String, Any?>())

        val persisted = UserRepository("profile", firestore).updateUser(afterForm, before)

        assertSame(write, persisted)
        assertEquals(before.copy(
            name = "After", email = "after@example.com", phoneNumber = "5678", lotteryAlertsEnabled = false
        ), saved)
        verify(profile, never()).set(any(User::class.java))
    }
}
