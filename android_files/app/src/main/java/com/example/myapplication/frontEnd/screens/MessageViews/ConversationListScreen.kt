package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Message
import com.example.myapplication.backEnd.firebaseRepositories.MessageRepository
import com.example.myapplication.backEnd.userModels.User

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You need to be logged in to see messages.")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Messages", fontSize = 22.sp)

        Spacer(Modifier.height(12.dp))

        if (isAdmin) {
            OutlinedButton(
                onClick = { navController.navigate("MessageLog") },
                modifier = Modifier.testTag("messageLogButton")
            ) {
                Text("Owner message log")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (conversations.isEmpty()) {
            Text(
                text = "No messages yet. You can message a Campsite Owner " +
                        "from a reservation on My Reservations.",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("conversationList"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        onClick = { navController.navigate("Conversation/$reservationId") }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromMessages")
        ) {
            Text("Back")
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
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (campsite.isNotBlank()) {
                Text(
                    text = campsite,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = preview,
                maxLines = 1,
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