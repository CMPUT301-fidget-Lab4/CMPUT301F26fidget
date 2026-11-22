package com.example.myapplication.backEnd.firebaseRepositories

import com.example.myapplication.backEnd.featureModels.AppNotification
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/** Reads and updates notifications stored in the "notifications" collection. */
class NotificationRepository(
    private val db: FirebaseFirestore = Firebase.firestore
) {
    private val collection = db.collection("notifications")

    /** Calls [onNew] once for each unread notification, including ones created while the app was closed. */
    fun listenForUnread(userId: String, onNew: (AppNotification) -> Unit): ListenerRegistration =
        collection
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { onNew(it.document.toObject(AppNotification::class.java)) }
            }

    /** Delivers the user's full history, newest first, whenever it changes. */
    fun listenForAll(userId: String, onChange: (List<AppNotification>) -> Unit): ListenerRegistration =
        collection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(AppNotification::class.java).orEmpty()
                onChange(list.sortedByDescending { it.createdAt })
            }

    fun markRead(notificationId: String) {
        collection.document(notificationId).update("read", true)
    }
}