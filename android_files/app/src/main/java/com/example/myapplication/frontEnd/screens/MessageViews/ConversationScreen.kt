package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Message
import com.example.myapplication.backEnd.firebaseRepositories.MessageRepository
import com.example.myapplication.backEnd.userModels.User

/**
 * One conversation between a campsite owner and an entrant about a reservation
 * (US 02.12.01, US 01.14.01).
 *
 * Shows the messages oldest first, with the user's own messages on the right.
 * Opening the screen, and any new message arriving while it's open, marks the
 * user's unread messages as read.
 *
 * Sending fails if the user isn't on the reservation or the reservation is no
 * longer active; the check happens in [MessageRepository.sendMessage].
 *
 * @param navController Used to go back.
 * @param reservationId The reservation this conversation is about.
 * @param user The logged-in user.
 * @param findUser Looks up a user by device ID, for showing names.
 * @param messageRepository Loads and sends messages.
 */
@Composable
fun ConversationScreen(
    navController: NavController,
    reservationId: String,
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
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Listen while the screen is open
    DisposableEffect(reservationId) {
        val registration = messageRepository.listenToConversation(reservationId) { messages = it }
        onDispose { registration.remove() }
    }

    // Mark as read and scroll to the newest message whenever messages change
    LaunchedEffect(messages.size) {
        if (messages.any { it.recipientId == user.deviceID && !it.read }) {
            messageRepository.markConversationRead(reservationId, user.deviceID)
        }
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val first = messages.firstOrNull()
    val otherId = first?.otherParticipant(user.deviceID)
    val title = otherId?.let { displayName(it, findUser) } ?: "Conversation"
    val campsite = first?.campsiteId.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()   // keep the text box above the keyboard
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            modifier = Modifier.testTag("conversationTitle")
        )

        if (campsite.isNotBlank()) {
            Text(
                text = campsite,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("messageList"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                item { Text("No messages yet.") }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message, mine = message.isFrom(user.deviceID))
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= Message.MAX_LENGTH) draft = it },
                placeholder = { Text("Write a message") },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("messageInput")
            )

            Spacer(Modifier.width(8.dp))

            Button(
                enabled = draft.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    messageRepository.sendMessage(reservationId, user, draft) { sent ->
                        sending = false
                        if (sent) {
                            draft = ""
                            errorMessage = null
                        } else {
                            errorMessage = "Couldn't send. This reservation may no longer be active."
                        }
                    }
                },
                modifier = Modifier.testTag("sendMessageButton")
            ) {
                Text("Send")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.testTag("returnFromConversation")
        ) {
            Text("Back")
        }
    }
}

/**
 * One message, on the right if the user sent it, on the left otherwise.
 */
@Composable
private fun MessageBubble(message: Message, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (mine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag(if (mine) "myMessage" else "theirMessage")
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.text)
                Text(
                    text = formatMessageTime(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}