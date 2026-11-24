package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Message
import com.example.myapplication.backEnd.firebaseRepositories.MessageRepository
import com.example.myapplication.backEnd.userModels.User

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Owner message log", fontSize = 22.sp)
        Text(
            text = "Every message sent from a campsite owner to an entrant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        when {
            !allowed -> Text(
                text = "Only admins can view this log.",
                modifier = Modifier
                    .weight(1f)
                    .testTag("messageLogNotAllowed")
            )

            messages.isEmpty() -> Text(
                text = "No messages yet.",
                modifier = Modifier.weight(1f)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("messageLogList"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    LogEntry(message, findUser)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromMessageLog")
        ) {
            Text("Back")
        }
    }
}

/**
 * One logged message: who sent it to whom, about which campsite, and when.
 */
@Composable
private fun LogEntry(message: Message, findUser: (String) -> User?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
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

            Spacer(Modifier.height(4.dp))

            Text(message.text)

            Text(
                text = formatMessageTime(message.sentAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}