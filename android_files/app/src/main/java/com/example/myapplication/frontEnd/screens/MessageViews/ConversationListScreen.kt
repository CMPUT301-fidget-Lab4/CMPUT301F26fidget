package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.example.myapplication.features.messaging.Message
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AppLayout

/**
 * Lists every conversation the user is part of, newest first.
 *
 * A conversation is all the messages about one reservation. Each row shows
 * the other person, the campsite, the latest message and how many are unread.
 * Tapping a row opens [ConversationScreen].
 *
 * Admins also get a button to the owner message log ([MessageLogScreen]).
 *
 * New conversations are started from a reservation on the reservations screen,
 * since every message has to belong to a reservation.
 *
 * @param navController Used to open conversations and go back.
 * @param user The logged-in user.
 * @param findUser Looks up a user by device ID, for showing names.
 * @param messageRepository Loads the user's messages.
 */
@Composable
fun ConversationListScreen(
    navController: NavController,
    user: User?,
    findUser: (String) -> User?,
    messageRepository: MessageRepository
) {
    if (user == null) {
        MessageListLayout("Messages", "returnFromMessages", onBack = { navController.popBackStack() }) {
            item { Text("You need to be logged in to see messages.") }
        }
        return
    }

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }

    // Listen while the screen is open
    DisposableEffect(user.deviceID) {
        val registration = messageRepository.listenToUserMessages(user.deviceID) { messages = it }
        onDispose { registration.remove() }
    }

    // Messages arrive newest first, so each group's first message is its latest,
    // and the groups stay in newest-first order.
    val conversations = messages.groupBy { it.reservationId }.entries.toList()
    val isAdmin = user.userType.equals("admin", ignoreCase = true)

    MessageListLayout(
        title = "Messages",
        backTag = "returnFromMessages",
        onBack = { navController.popBackStack() },
        modifier = Modifier.testTag("conversationList")
    ) {
        if (isAdmin) {
            item {
                OutlinedButton(
                    onClick = { navController.navigate("MessageLog") { launchSingleTop = true } },
                    modifier = Modifier.testTag("messageLogButton")
                ) {
                    Text("Owner message log")
                }
            }
        }

        if (conversations.isEmpty()) {
            item { Text("No messages yet. You can start messaging from a reservation on My Reservations.") }
        } else {
            items(conversations, key = { it.key }) { (reservationId, thread) ->
                val latest = thread.first()
                val otherId = latest.otherParticipant(user.deviceID).orEmpty()
                val unread = thread.count { it.recipientId == user.deviceID && !it.read }
                val preview = (if (latest.isFrom(user.deviceID)) "You: " else "") + latest.text

                ConversationRow(
                    name = displayName(otherId, findUser),
                    campsite = latest.campsiteId,
                    preview = preview,
                    time = formatMessageTime(latest.sentAt),
                    unread = unread,
                    onClick = {
                        navController.navigate("Conversation/$reservationId") { launchSingleTop = true }
                    }
                )
            }
        }
    }
}

/**
 * One conversation in the list.
 */
@Composable
private fun ConversationRow(
    name: String,
    campsite: String,
    preview: String,
    time: String,
    unread: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("conversationRow")
    ) {
        Column(
            modifier = Modifier.padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            Text(text = name, fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal)
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (campsite.isNotBlank()) {
                Text(
                    text = campsite,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = preview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (unread > 0) {
                Text(
                    text = "$unread unread",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("unreadCount")
                )
            }
        }
    }
}
