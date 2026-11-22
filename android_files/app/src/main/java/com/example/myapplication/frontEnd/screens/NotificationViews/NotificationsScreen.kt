package com.example.myapplication.frontEnd.screens.NotificationViews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.AppNotification
import com.example.myapplication.backEnd.firebaseRepositories.NotificationRepository
import com.example.myapplication.backEnd.userModels.User

/**
 * Shows the user's lottery result notifications, newest first.
 *
 * Listens to the notifications collection for [user] while the screen is
 * visible and stops listening when it leaves the screen. Shows a message
 * when there are no notifications yet.
 *
 * @param navController used to return to the previous screen
 * @param user the logged-in user whose notifications are shown
 * @param notificationRepository source of the user's notifications
 */
@Composable
fun NotificationsScreen(
    navController: NavController,
    user: User,
    notificationRepository: NotificationRepository
) {
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }

    DisposableEffect(user.deviceID) {
        val registration = notificationRepository.listenForAll(user.deviceID) { notifications = it }
        onDispose { registration.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
            Text("Notifications", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        if (notifications.isEmpty()) {
            Text("No notifications yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notifications, key = { it.id }) { n ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                if (n.type == "WIN") "You won!" else "Lottery result",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(n.message)
                        }
                    }
                }
            }
        }
    }
}