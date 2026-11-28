package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.messaging.Message
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

private val ShortConversationHeight = 320.dp
private val InlineConversationActionsWidth = 360.dp

/** Shows and sends messages between the owner and entrant for a reservation. */
@Composable
fun ConversationScreen(
    navController: NavController,
    reservationId: String,
    user: User?,
    findUser: (String) -> User?,
    messageRepository: MessageRepository
) {
    val goBack: () -> Unit = { navController.popBackStack() }
    if (user == null) {
        MessageListLayout("Conversation", "returnFromConversation", onBack = goBack) {
            item { Text("You need to be logged in to see messages.") }
        }
        return
    }

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var draft by rememberSaveable(reservationId) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    DisposableEffect(reservationId) {
        val registration = messageRepository.listenToConversation(reservationId) { messages = it }
        onDispose { registration.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.any { it.recipientId == user.deviceID && !it.read }) {
            messageRepository.markConversationRead(reservationId, user.deviceID)
        }
    }

    val first = messages.firstOrNull()
    val otherId = first?.otherParticipant(user.deviceID)
    val title = otherId?.let { displayName(it, findUser) } ?: "Conversation"
    val campsite = first?.campsiteId.orEmpty()
    val showSendError = errorMessage != null

    LaunchedEffect(messages.size, showSendError) {
        // Item zero is the conversation heading. Scroll past the empty state or last
        // message when there is an error to show at the end of the list.
        if (showSendError) listState.animateScrollToItem(maxOf(messages.size, 1) + 1)
        else if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    AdaptiveContent {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(horizontal = AppLayout.Medium, vertical = AppLayout.Small)
        ) {
            val shortViewport = maxHeight < ShortConversationHeight
            val backInComposer = shortViewport && maxWidth >= InlineConversationActionsWidth
            val draftLines = when {
                shortViewport -> 1
                LocalDensity.current.fontScale >= 1.8f -> 2
                else -> 4
            }
            val maxBubbleWidth = maxWidth * 0.82f

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("messageList"),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.testTag("conversationTitle")
                            )
                            if (campsite.isNotBlank()) {
                                Text(
                                    text = campsite,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (messages.isEmpty()) item { Text("No messages yet.") }
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message, mine = message.isFrom(user.deviceID), maxWidth = maxBubbleWidth)
                    }
                    errorMessage?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error) }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= Message.MAX_LENGTH) draft = it },
                        placeholder = { Text("Write a message") },
                        maxLines = draftLines,
                        modifier = Modifier.weight(1f).testTag("messageInput")
                    )
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
                    if (backInComposer) ConversationBackButton(goBack)
                }

                if (!backInComposer) ConversationBackButton(goBack)
            }
        }
    }
}

@Composable
private fun ConversationBackButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.testTag("returnFromConversation")) {
        Text("Back")
    }
}

/** Own messages align right and other messages align left. */
@Composable
private fun MessageBubble(message: Message, mine: Boolean, maxWidth: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (mine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = maxWidth).testTag(if (mine) "myMessage" else "theirMessage")
        ) {
            Column(Modifier.padding(AppLayout.Small)) {
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