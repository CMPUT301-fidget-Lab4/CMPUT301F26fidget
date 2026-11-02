
package com.example.myapplication.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable
fun LotteryManualTest() {

    val campsiteRepo = remember { CampsiteRepository() }

    val lotteryRepo = remember { LotteryRepository() }

    val services = remember {
        Services(lotteryRepo, campsiteRepo)
    }

    // Only for saving a campsite
    var campsiteName by rememberSaveable { mutableStateOf("") }
    var occupancy by rememberSaveable { mutableStateOf("") }

    // Only for lottery operations
    var lotteryCampsites by rememberSaveable { mutableStateOf("") }
    var numWinners by rememberSaveable { mutableStateOf("") }
    var waitlistCapacity by rememberSaveable { mutableStateOf("") }

    var registrationStart by rememberSaveable { mutableStateOf("") }
    var registrationEnd by rememberSaveable { mutableStateOf("") }
    var bookingStart by rememberSaveable { mutableStateOf("") }
    var bookingEnd by rememberSaveable { mutableStateOf("") }

    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun validName(name: String): Boolean {
        return name.isNotBlank() && !name.contains("/")
    }

    fun lotteryNames(): List<String> {
        return lotteryCampsites
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    // ---------- SAVE CAMPSITE ----------

    fun saveCampsite() {
        val name = campsiteName.trim()
        val number = occupancy.toIntOrNull()

        if (!validName(name) || number == null || number < 1) {
            result = "Enter a valid campsite name and occupancy."
            return
        }

        if (campsiteRepo.campsiteExists(name)) {
            result = "Campsite already exists."
            return
        }

        val campsite = Campsite(
            name = name,
            occupancy = number
        )

        if (campsiteRepo.addCampsite(campsite)) {
            result = "Campsite save requested: $name"
        } else {
            result = "Could not save campsite."
        }
    }

    // ---------- CREATE LOTTERY ----------

    fun createLottery() {
        if (busy) return

        // ONLY use the Lottery Campsites text field
        val ids = lotteryNames()

        if (ids.isEmpty() || ids.any { !validName(it) }) {
            result = "Enter valid lottery campsite names."
            return
        }

        val winners = numWinners.toIntOrNull()
        val capacity = waitlistCapacity.toIntOrNull()

        if (winners == null || winners < 1) {
            result = "Number of winners must be at least 1."
            return
        }

        if (capacity == null || capacity < 0) {
            result = "Waitlist capacity must be 0 or greater."
            return
        }

        val period = try {
            Reservation(
                registrationStart = LocalDate.parse(registrationStart),
                registrationEnd = LocalDate.parse(registrationEnd),
                bookingStart = LocalDate.parse(bookingStart),
                bookingEnd = LocalDate.parse(bookingEnd)
            )
        } catch (e: DateTimeParseException) {
            result = "Use YYYY-MM-DD for all dates."
            return
        }

        if (!period.hasValidDates()) {
            result = "Invalid registration or booking dates."
            return
        }

        if (!services.canCreateLottery(ids)) {
            result = "A campsite is missing or already in a lottery."
            return
        }

        // First name is the main campsite
        val mainName = ids.first()
        val campsite = campsiteRepo.findCampsite(mainName)

        if (campsite == null) {
            result = "Campsite not found."
            return
        }

        // Remaining names are the other campsites
        val others = ids.drop(1)

        if (!campsite.createLottery(winners, capacity, others)) {
            result = "Could not create lottery."
            return
        }

        if (!campsite.setReservation(
                period.registrationStart,
                period.registrationEnd
            )
        ) {
            result = "Invalid registration dates."
            return
        }

        if (!campsite.setBookingPeriod(
                period.bookingStart,
                period.bookingEnd
            )
        ) {
            result = "Invalid booking dates."
            return
        }

        busy = true
        result = "Saving lottery..."

        // Repository saves to ALL lottery campsites
        // and updates their flags.
        lotteryRepo.saveLottery(mainName, campsite.lottery)
            .addOnSuccessListener {
                result = """
                    Lottery saved!

                    ID: ${campsite.lottery.id}

                    Campsites:
                    ${ids.joinToString("\n")}

                    All campsite flags set to true.
                """.trimIndent()

                busy = false
            }
            .addOnFailureListener { error ->
                result = "Save failed: ${error.message}"
                busy = false
            }
    }

    // ---------- GET LOTTERY ----------

    fun getLottery() {
        if (busy) return

        // ONLY use the Lottery Campsites text field
        val ids = lotteryNames()

        if (ids.size != 1 || !validName(ids.first())) {
            result = "Enter one campsite in Lottery Campsites to get its lottery."
            return
        }

        result = "Loading lottery..."

        lotteryRepo.getLottery(ids.first()) { lottery ->
            result = if (lottery == null) {
                "Lottery not found or read failed."
            } else {
                """
                    Lottery Found!

                    ID: ${lottery.id}

                    Campsites:
                    ${lottery.campsiteIds.joinToString("\n")}

                    Winners: ${lottery.numWinners}
                    Capacity: ${lottery.waitlistCapacity}

                    Registration:
                    ${lottery.period.registrationStart}
                    to ${lottery.period.registrationEnd}

                    Booking:
                    ${lottery.period.bookingStart}
                    to ${lottery.period.bookingEnd}
                """.trimIndent()
            }
        }
    }

    // ---------- DELETE LOTTERY ----------

    fun deleteLottery() {
        if (busy) return

        // ONLY use the Lottery Campsites text field
        val ids = lotteryNames()

        if (ids.size != 1 || !validName(ids.first())) {
            result = "Enter one campsite in Lottery Campsites to delete its lottery."
            return
        }

        busy = true
        result = "Deleting lottery..."

        // Repository finds all participating campsites,
        // deletes their lottery copies and resets flags.
        lotteryRepo.deleteLottery(ids.first())
            .addOnSuccessListener {
                result = """
                    Lottery deleted!

                    All participating campsites:

                    inLottery = false
                    reservationSet = false
                    bookingSet = false
                """.trimIndent()

                busy = false
            }
            .addOnFailureListener { error ->
                result = "Delete failed: ${error.message}"
                busy = false
            }
    }

    // ---------- UI ----------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Lottery Manual Test",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        HorizontalDivider()

        Text(
            text = "Save Campsite",
            style = MaterialTheme.typography.titleMedium
        )

        LotteryTestInput(
            campsiteName,
            { campsiteName = it },
            "Campsite Name"
        )

        LotteryTestInput(
            occupancy,
            { occupancy = it },
            "Occupancy",
            numeric = true
        )

        Button(
            onClick = { saveCampsite() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. Save Campsite")
        }

        Text(
            text = "Loaded Campsites:\n" +
                    campsiteRepo.campsites
                        .joinToString(", ") { it.name }
                        .ifBlank { "None" },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Text(
            text = "Lottery",
            style = MaterialTheme.typography.titleMedium
        )

        LotteryTestInput(
            lotteryCampsites,
            { lotteryCampsites = it },
            "Lottery Campsites (comma-separated)"
        )

        LotteryTestInput(
            numWinners,
            { numWinners = it },
            "Number of Winners",
            numeric = true
        )

        LotteryTestInput(
            waitlistCapacity,
            { waitlistCapacity = it },
            "Waitlist Capacity (0 = Unlimited)",
            numeric = true
        )

        Text(
            text = "Registration Dates",
            style = MaterialTheme.typography.titleMedium
        )

        LotteryTestInput(
            registrationStart,
            { registrationStart = it },
            "Registration Start (YYYY-MM-DD)"
        )

        LotteryTestInput(
            registrationEnd,
            { registrationEnd = it },
            "Registration End (YYYY-MM-DD)"
        )

        Text(
            text = "Booking Dates",
            style = MaterialTheme.typography.titleMedium
        )

        LotteryTestInput(
            bookingStart,
            { bookingStart = it },
            "Booking Start (YYYY-MM-DD)"
        )

        LotteryTestInput(
            bookingEnd,
            { bookingEnd = it },
            "Booking End (YYYY-MM-DD)"
        )

        HorizontalDivider()

        Button(
            onClick = { createLottery() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Create Lottery")
        }

        Button(
            onClick = { getLottery() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. Get Lottery")
        }

        Button(
            onClick = { deleteLottery() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("4. Delete Lottery")
        }

        HorizontalDivider()

        Text(
            text = "Results",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = result,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LotteryTestInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) {
                KeyboardType.Number
            } else {
                KeyboardType.Text
            }
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
