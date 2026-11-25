package com.example.myapplication.frontEnd.screens.ReservationBookingViews

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.backEnd.featureModels.Reservation
import com.example.myapplication.backEnd.featureModels.ReservationStatus
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.backEnd.userModels.User
import com.google.firebase.firestore.FirebaseFirestoreException
import java.time.format.DateTimeFormatter
import kotlin.collections.filter


private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Shows the campsite owner what dates are booked and for which campsite and entrant.
 *
 * @param navController Used for the back button.
 * @param user The logged-in user.
 * @param reservationRepository Loads and changes reservations.
 * @param users Loaded profiles used to show entrant names to management.
 */
@Composable
fun ReservedDatesScreen(
    navController: NavController,
    user: User?,
    reservationRepository: ReservationRepository,
    users: List<User> = emptyList(),

) {

    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You need to be logged in to see reservations.")
        }
        return
    }

    val isOwner = user.userType.trim().equals("campsite owner", ignoreCase = true)

    if(!isOwner){
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You need to be a owner to see the reserved dates.")
        }
        return
    }

    var reservations by remember(user.deviceID, user.userType) { mutableStateOf<List<Reservation>>(emptyList()) }
    var errorMessage by remember(user.deviceID, user.userType) { mutableStateOf<String?>(null) }
    var loadError by remember(user.deviceID, user.userType) { mutableStateOf<String?>(null) }
    var loading by remember(user.deviceID, user.userType) { mutableStateOf(true) }
    var retryCount by remember(user.deviceID, user.userType) { mutableStateOf(0) }


    DisposableEffect(user.deviceID, user.userType, reservationRepository) {
        loading = true
        loadError = null
        var listening = true
        val onReadError: (Exception) -> Unit = { error ->
            if (listening) {
                Log.e("Reservations", "Could not load reservations", error)
                loading = false
                loadError = when ((error as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Your account cannot read these reservations."
                    FirebaseFirestoreException.Code.UNAVAILABLE -> "Unable to connect. Check your internet connection and try again."
                    else -> "Unable to load reservations. Please try again."
                }
            }
        }
        val onCacheStatus: (Boolean) -> Unit = {}
        val onReservations: (List<Reservation>) -> Unit = { loaded ->
            if (listening) {
                reservations = loaded
                loading = false
                loadError = null
            }
        }
        val registration = reservationRepository.listenForOwner(
            user.deviceID,
            onReadError,
            onCacheStatus,
            onReservations
        )
        onDispose {
            listening = false
            registration.remove()
        }
    }

        val reservation = ReservationStatus.RESERVED.let { selected ->
            reservations.filter { it.status == ReservationStatus.RESERVED }
        } ?: reservations

        val sortedReservation = reservation.sortedBy { it.bookingStartDate }

        val displayedReservation = listOf(
            "Reserved Dates:" to sortedReservation
        )



    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    ) {
        Text(text = "Reserved Dates: ", fontSize = 22.sp)

        Spacer(Modifier.height(12.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }


        loadError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("reservationLoadError")
            )
            TextButton(
                onClick = { retryCount++ },
                modifier = Modifier.testTag("reservationRetryButton")
            ) {
                Text("Retry")
            }
        }



        if (sortedReservation.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            when {
                loadError != null -> Unit
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.testTag("reservationsLoading"))
                    Text("Checking for reservations...")
                    if (!loading) {
                        Text("No saved reservations on this device. Check your internet connection to load new invitations.")
                        TextButton(onClick = { retryCount++ }) { Text("Retry") }
                    }
                }

                else -> Text("No reservations yet.", modifier = Modifier.testTag("reservationsEmpty"))
            }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("reservedDatesList"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((title, list) in displayedReservation) {
                    if (list.isEmpty()) continue

                    item(key = title) {
                        Text(
                            title,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(list, key = { it.id }) { reservation ->
                        ReservedDatesCard(
                            reservation = reservation,
                            entrant = users.firstOrNull { it.deviceID == reservation.entrantId },
                        )
                    }
                }
            }



    }
        Button(
            onClick = {
                navController.popBackStack()
            },
            modifier = Modifier.testTag("reservationMenuBack")
        ) {
            Text("Back")
        }

    }
}

@Composable
private fun ReservedDatesCard(
    reservation: Reservation,
    entrant: User?
){
    val start = reservation.bookingStartDate
    val end = reservation.bookingEndDate
    var fetchedCampsites by remember(reservation.lotteryId) { mutableStateOf<List<String>>(emptyList()) }
    val names = fetchedCampsites

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            if (reservation.campsiteId.isBlank() && names.isNotEmpty()) {
                Text("Campsites: ${names.take(3).joinToString()}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (names.size > 3) Text("+${names.size - 3} more")
            }

            if (start != null && end != null) {
                Text("Check-in: ${start.format(dateFormat)}")
                Text("Check-out: ${end.format(dateFormat)}")
            }

            Text("Entrant: ${entrant?.name?.takeIf { it.isNotBlank() } ?: "Name unavailable"}")

            Text("Status: ${statusLabel(reservation.status)}")


        }
    }
}


private fun statusLabel(status: ReservationStatus): String =
    status.name.lowercase().replaceFirstChar { it.uppercase() }