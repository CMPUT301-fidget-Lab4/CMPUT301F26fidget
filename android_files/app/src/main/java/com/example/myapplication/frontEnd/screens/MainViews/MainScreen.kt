package com.example.myapplication.frontEnd.screens.mainViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.example.myapplication.features.notification.AppNotification
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.NotificationViews.NOTIFICATIONS_ROUTE
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

private data class MainDestination(val label: String, val route: String, val testTag: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    userProfile: User?,
    notifications: List<AppNotification> = emptyList()
) {
    if (userProfile == null) return

    val destinations = buildList {
        add(MainDestination("Notifications", NOTIFICATIONS_ROUTE, "viewNotifications"))
        add(MainDestination("Search for campsites", "CampsiteList", "campsiteSearch"))

        if (userProfile.userType == "admin" || userProfile.userType == "campsite owner") {
            add(MainDestination("Create Lottery", "CreateLotteryScreen", "createLotteryButton"))
        }
        if (userProfile.userType == "admin") {
            add(MainDestination("View Profiles", "AdminProfilesView", "adminProfileBrowse"))
        }

        add(MainDestination("My joined lotteries", "LotteryInformation", "viewLotteryInformation"))
        if (userProfile.userType.trim().equals("campsite owner", true)) {
            add(MainDestination("My campsite lotteries", "MyCampsiteLotteries", "viewOwnedLotteries"))
        }
        if (userProfile.userType.trim().equals("admin", true)) {
            add(MainDestination("All active lotteries", "AllActiveLotteries", "viewAllActiveLotteries"))
        }

        add(MainDestination("My Reservations", "Reservations", "viewReservations"))
        add(MainDestination("View Messages", "Messages", "viewMessages"))
        if (userProfile.userType == "campsite owner" || userProfile.userType == "admin") {
            add(MainDestination("View my campsites", "MyCampsiteList", "viewMyCampsites"))
        }
        add(MainDestination("Profile", "ProfileScreen", "ProfileScreen"))
    }

    AdaptiveContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome ${userProfile.name}!",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            notifications.firstOrNull()?.let { result ->
                Text(
                    text = result.message,
                    modifier = Modifier.testTag("latestLotteryResult"),
                    textAlign = TextAlign.Center
                )
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = if (maxWidth >= AppLayout.TwoColumnWidth) 2 else 1,
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.Small),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
                ) {
                    destinations.forEach { destination ->
                        Button(
                            onClick = {
                                navController.navigate(destination.route) { launchSingleTop = true }
                            },
                            modifier = Modifier.weight(1f).testTag(destination.testTag)
                        ) {
                            Text(destination.label, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
