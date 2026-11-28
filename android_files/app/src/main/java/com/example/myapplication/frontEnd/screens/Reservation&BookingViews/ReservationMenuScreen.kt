package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import com.example.myapplication.features.booking.Reservation
import com.example.myapplication.features.booking.ReservationStatus
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
import java.time.LocalDateTime

enum class ReservationCategory(val route: String, val title: String) {
    CURRENT("current", "Current reservations"),
    PAST("past", "Past reservations"),
    CANCELLED("cancelled", "Cancelled reservations");

    fun includes(reservation: Reservation, now: LocalDateTime): Boolean = when (this) {
        CURRENT -> reservation.isActive(now)
        PAST -> reservation.isPast(now)
        CANCELLED -> reservation.status in setOf(ReservationStatus.CANCELLED, ReservationStatus.REJECTED, ReservationStatus.DECLINED)
    }
}

@Composable
fun ReservationMenuScreen(navController: NavController) {
    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppLayout.Large),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("View reservations", style = MaterialTheme.typography.headlineMedium)
                ReservationCategory.entries.forEach { category ->
                    Button(
                        onClick = { navController.navigate("Reservations/${category.route}") { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth().testTag("${category.route}Reservations")
                    ) { Text(category.title) }
                }
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.testTag("reservationMenuBack")) {
                    Text("Back")
                }
            }
        }
    }
}
