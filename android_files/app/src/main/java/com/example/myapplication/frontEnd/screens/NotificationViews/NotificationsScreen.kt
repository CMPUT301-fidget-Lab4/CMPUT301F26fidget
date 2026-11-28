package com.example.myapplication.frontEnd.screens.NotificationViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.features.notification.LotteryNotificationInbox
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

internal const val NOTIFICATIONS_ROUTE = "NotificationsScreen"

/** Shows persisted lottery outcomes independently of the device alert preference. */
@Composable
fun NotificationsScreen(
    inbox: LotteryNotificationInbox?,
    onBack: () -> Unit
) {
    val notice = when {
        inbox == null || inbox.loading -> "Loading notifications…"
        inbox.loadFailed -> "Could not load all notifications."
        inbox.fromCache -> "Showing saved notifications while checking for updates."
        inbox.notifications.isEmpty() -> "No notifications yet."
        else -> null
    }
    AdaptiveContent {
        Column(
            Modifier.fillMaxSize().padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("notificationsBack")) {
                    Text("Back")
                }
                Text(
                    "Notifications",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("notificationsList"),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                notice?.let { message -> item { Text(message) } }
                items(inbox?.notifications.orEmpty(), key = { it.id }) { notification ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(AppLayout.Medium)) {
                            Text(
                                if (notification.type == "WIN") "You won!" else "Lottery result",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(notification.message)
                        }
                    }
                }
            }
        }
    }
}
