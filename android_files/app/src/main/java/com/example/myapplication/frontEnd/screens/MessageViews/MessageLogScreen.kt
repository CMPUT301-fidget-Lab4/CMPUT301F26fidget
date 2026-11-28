package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavController
import com.example.myapplication.features.messaging.Message
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AppLayout

/**
 * Admin log of every message sent from a campsite owner to an entrant,
 * newest first (US 03.12.01).
 *
 * Non-admins see a message saying they can't view the log.
 *
 * @param navController Used to go back.
 * @param user The logged-in user, who must be an admin.
 * @param findUser Looks up a user by device ID, for showing names.
 * @param messageRepository Loads the messages.
 */
@Composable
fun MessageLogScreen(
    navController: NavController,
    user: User?,
    findUser: (String) -> User?,
    messageRepository: MessageRepository
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var allowed by remember { mutableStateOf(true) }

    // Listen while the screen is open. The repository refuses non-admins.
    DisposableEffect(user?.deviceID) {
        val registration = user?.let {
            messageRepository.listenToOwnerMessages(it) { list -> messages = list }
        }
        allowed = registration != null
        onDispose { registration?.remove() }
    }

    MessageListLayout(
        title = "Owner message log",
        subtitle = "Every message sent from a campsite owner to an entrant.",
        backTag = "returnFromMessageLog",
        onBack = { navController.popBackStack() },
        modifier = Modifier.testTag("messageLogList")
    ) {
        when {
            !allowed -> item {
                Text("Only admins can view this log.", modifier = Modifier.testTag("messageLogNotAllowed"))
            }
            messages.isEmpty() -> item { Text("No messages yet.") }
            else -> items(messages, key = { it.id }) { message -> LogEntry(message, findUser) }
        }
    }
}

/**
 * One logged message: who sent it to whom, about which campsite, and when.
 */
@Composable
private fun LogEntry(message: Message, findUser: (String) -> User?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            Text(
                text = "${displayName(message.senderId, findUser)} → " +
                        displayName(message.recipientId, findUser),
                fontWeight = FontWeight.Bold
            )

            if (message.campsiteId.isNotBlank()) {
                Text(
                    text = message.campsiteId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(message.text)

            Text(
                text = formatMessageTime(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
