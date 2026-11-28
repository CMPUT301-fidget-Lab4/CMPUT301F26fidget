package com.example.myapplication.frontEnd.screens.MessageViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val timeOnly = DateTimeFormatter.ofPattern("h:mm a")
private val dateAndTime = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
internal fun MessageListLayout(
    title: String,
    backTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit
) {
    AdaptiveContent {
        Column(
            modifier = Modifier.fillMaxSize().padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            LazyColumn(
                modifier = modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                item { Text(title, style = MaterialTheme.typography.titleLarge) }
                subtitle?.let {
                    item {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                content()
            }
            Button(onClick = onBack, modifier = Modifier.testTag(backTag)) { Text("Back") }
        }
    }
}

/**
 * A user's name for showing in the message screens.
 *
 * @param userId The user's device ID.
 * @param findUser Looks up a user by device ID.
 * @return Their name, or "Unknown user" if they can't be found (for example a deleted profile).
 */
internal fun displayName(userId: String, findUser: (String) -> User?): String =
    findUser(userId)?.name?.ifBlank { null } ?: "Unknown user"

/**
 * Formats when a message was sent: just the time if it was today,
 * otherwise the date and time.
 *
 * @param sentAt When the message was sent, in milliseconds.
 */
internal fun formatMessageTime(sentAt: Long): String {
    val sent = Instant.ofEpochMilli(sentAt).atZone(AlbertaTime.zone)
    val today = LocalDate.now(AlbertaTime.zone)
    return if (sent.toLocalDate() == today) sent.format(timeOnly) else sent.format(dateAndTime)
}
