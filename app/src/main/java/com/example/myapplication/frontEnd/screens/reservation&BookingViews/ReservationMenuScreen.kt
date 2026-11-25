package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
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
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("View reservations", style = MaterialTheme.typography.headlineMedium)

        
        Button(
                onClick = { navController.navigate("Reservations/ReservedDates") { launchSingleTop = true } },
                modifier = Modifier.fillMaxWidth().testTag("ReservedDates")
            ) { Text("Reserved Dates") }
        ReservationCategory.entries.forEach { category ->
            Button(
                onClick = { navController.navigate("Reservations/${category.route}") { launchSingleTop = true } },
                modifier = Modifier.fillMaxWidth().testTag("${category.route}Reservations")
            ) { Text(category.title) }
        }

        Button(onClick = { navController.popBackStack() }, modifier = Modifier.testTag("reservationMenuBack")) { Text("Back") }
    }
}
