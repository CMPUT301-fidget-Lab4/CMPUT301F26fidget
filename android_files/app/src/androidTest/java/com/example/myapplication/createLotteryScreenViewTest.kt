package com.example.myapplication.ui_components.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.LotteryRepository
import com.example.myapplication.features.Registration
import com.example.myapplication.features.User
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Screen for creating a campsite lottery.
 *
 * Allows the user to select participating campsites,
 * configure registration dates, choose the number of
 * winners, and set the waitlist capacity.
 *
 * Uses [LotteryRepository] to save the lottery in Firestore.
 *
 * UI elements include test tags for automated Compose tests.
 *
 * @param navController Navigation controller for returning
 * to previous screens.
 * @param user Signed-in user. Only Admin or Owner roles may create lotteries.
 *             Owners may only select campsites in [User.campsiteIds].
 * @param campsites Available campsites indexed by name.
 * @param lotteryRepository Repository provided by the caller for creating lotteries.
 */
@Composable
fun CreateLotteryScreen(
    navController: NavController,
    user: User,
    campsites: Map<String, Campsite>,
    lotteryRepository: LotteryRepository
) {
    val context = LocalContext.current

    // Works when userType is an enum or string (e.g. ADMIN, OWNER, CAMPSITE_OWNER).
    // If your project uses a UserType enum, prefer direct enum comparisons here.
    val role = user.userType.toString()
        .trim()
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")
        .lowercase()

    val isAdmin = role == "admin"
    val isOwner = role == "owner" || role == "campsiteowner"
    val canCreateLottery = isAdmin || isOwner

    // Administrators can use any loaded campsite; owners can use only those
    // whose IDs appear in their campsiteIds map AND in the loaded repository map.
    val eligibleCampsites: Map<String, Campsite> = when {
        isAdmin -> campsites
        isOwner -> campsites.filterKeys { it in user.campsiteIds }
        else -> emptyMap()
    }
    val campsiteNames = eligibleCampsites.keys.sorted()

    // Campsite selection
    var campsiteName by rememberSaveable {
        mutableStateOf("")
    }

    val additionalCampsites = remember {
        mutableStateListOf<String>()
    }

    var dropdownExpanded by remember {
        mutableStateOf(false)
    }

    // Lottery settings
    var numWinners by rememberSaveable {
        mutableStateOf("1")
    }

    var waitlistCapacity by rememberSaveable {
        mutableStateOf("5")
    }

    // Default registration: one hour from now, ending the next day
    val defaultStart = remember {
        LocalDateTime.now()
            .plusHours(1)
            .withSecond(0)
            .withNano(0)
    }

    var startDate by rememberSaveable {
        mutableStateOf(defaultStart.toLocalDate().toString())
    }

    var startTime by rememberSaveable {
        mutableStateOf(defaultStart.toLocalTime().toString())
    }

    var endDate by rememberSaveable {
        mutableStateOf(defaultStart.plusDays(1).toLocalDate().toString())
    }

    var endTime by rememberSaveable {
        mutableStateOf(defaultStart.plusDays(1).toLocalTime().toString())
    }

    // Creation status
    var result by rememberSaveable {
        mutableStateOf("")
    }

    var isSuccess by remember {
        mutableStateOf(false)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("h:mm a")
    }

    if (!canCreateLottery) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("createLotteryUnauthorized"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Only an administrator or campsite owner can create a lottery.")
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Back")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("createLotteryScreen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.testTag("createLotteryBackButton")
            ) {
                Text("Back")
            }
        }

        Text(
            text = "Create Lottery",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("createLotteryTitle")
        )

        Text(
            text = "Set up a lottery for your campsite and manage how visitors can register.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        HorizontalDivider()

        // Primary campsite
        Text(
            text = "Select Campsite",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Choose the campsite for this lottery.",
            modifier = Modifier.fillMaxWidth()
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                enabled = campsiteNames.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("createLotteryCampsiteDropdown")
            ) {
                Text(
                    text = campsiteName.ifEmpty {
                        "Choose a campsite"
                    }
                )
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = {
                    dropdownExpanded = false
                }
            ) {
                campsiteNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            campsiteName = name
                            additionalCampsites.remove(name)
                            dropdownExpanded = false
                            result = ""
                        },
                        modifier = Modifier.testTag(
                            "createLotteryPrimaryOption_$name"
                        )
                    )
                }
            }
        }

        if (campsiteNames.isEmpty()) {
            Text(
                text = if (isOwner) {
                    "You do not have any owned campsites available."
                } else {
                    "No campsites available. Register a campsite first."
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("createLotteryNoCampsites")
            )
        }

        // Additional campsite selection
        if (campsiteNames.size > 1 && campsiteName.isNotEmpty()) {
            HorizontalDivider()

            Text(
                text = "Additional Campsites",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Optional: Include other campsites in this lottery.",
                modifier = Modifier.fillMaxWidth()
            )

            campsiteNames
                .filter { it != campsiteName }
                .forEach { name ->

                    val isSelected = name in additionalCampsites

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    additionalCampsites.remove(name)
                                } else {
                                    additionalCampsites.add(name)
                                }
                            }
                            .testTag("createLotteryAdditionalOption_$name"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (name !in additionalCampsites) {
                                        additionalCampsites.add(name)
                                    }
                                } else {
                                    additionalCampsites.remove(name)
                                }
                            },
                            modifier = Modifier.testTag(
                                "createLotteryAdditionalCheckbox_$name"
                            )
                        )

                        Text(text = name)
                    }
                }
        }

        HorizontalDivider()

        // Number of winners
        Text(
            text = "Lottery Settings",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = numWinners,
            onValueChange = { numWinners = it },
            label = { Text("Number of Winners") },
            supportingText = {
                Text("How many entrants should win?")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("createLotteryNumWinnersField")
        )

        OutlinedTextField(
            value = waitlistCapacity,
            onValueChange = { waitlistCapacity = it },
            label = { Text("Waitlist Capacity") },
            supportingText = {
                Text("Maximum number of entrants allowed on the waitlist.")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("createLotteryWaitlistCapacityField")
        )

        HorizontalDivider()

        // Registration period
        Text(
            text = "Registration Period",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Choose when visitors can start and stop registering.",
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Registration Starts",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val current = LocalDate.parse(startDate)

                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            startDate = LocalDate.of(
                                year, month + 1, day
                            ).toString()
                        },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("createLotteryStartDateButton")
            ) {
                Text(
                    LocalDate.parse(startDate)
                        .format(dateFormatter)
                )
            }

            OutlinedButton(
                onClick = {
                    val current = LocalTime.parse(startTime)

                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            startTime = LocalTime.of(
                                hour, minute
                            ).toString()
                        },
                        current.hour,
                        current.minute,
                        false
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("createLotteryStartTimeButton")
            ) {
                Text(
                    LocalTime.parse(startTime)
                        .format(timeFormatter)
                )
            }
        }

        Text(
            text = "Registration Ends",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val current = LocalDate.parse(endDate)

                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            endDate = LocalDate.of(
                                year, month + 1, day
                            ).toString()
                        },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("createLotteryEndDateButton")
            ) {
                Text(
                    LocalDate.parse(endDate)
                        .format(dateFormatter)
                )
            }

            OutlinedButton(
                onClick = {
                    val current = LocalTime.parse(endTime)

                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            endTime = LocalTime.of(
                                hour, minute
                            ).toString()
                        },
                        current.hour,
                        current.minute,
                        false
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("createLotteryEndTimeButton")
            ) {
                Text(
                    LocalTime.parse(endTime)
                        .format(timeFormatter)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Create lottery
        Button(
            enabled = !isLoading && campsiteNames.isNotEmpty(),
            onClick = {
                val name = campsiteName.trim()
                val winners = numWinners.toIntOrNull()
                val capacity = waitlistCapacity.toIntOrNull()

                val start = LocalDateTime.of(
                    LocalDate.parse(startDate),
                    LocalTime.parse(startTime)
                )

                val end = LocalDateTime.of(
                    LocalDate.parse(endDate),
                    LocalTime.parse(endTime)
                )

                isSuccess = false

                when {
                    name.isEmpty() -> {
                        result = "Please select a campsite."
                    }

                    name !in eligibleCampsites -> {
                        result = "You do not have permission to use this campsite."
                    }

                    additionalCampsites.any { it !in eligibleCampsites || it == name } -> {
                        result = "One or more additional campsites are not permitted."
                    }

                    winners == null || winners < 1 -> {
                        result = "Number of winners must be at least 1."
                    }

                    capacity == null || capacity < 0 -> {
                        result = "Waitlist capacity must be 0 or greater."
                    }

                    !start.isBefore(end) -> {
                        result = "Registration must end after it starts."
                    }

                    else -> {
                        isLoading = true
                        result = ""

                        val registration = Registration(
                            registrationStart = start,
                            registrationEnd = end
                        )

                        lotteryRepository.createLottery(
                            campsiteName = name,
                            period = registration,
                            numWinners = winners,
                            campsites = eligibleCampsites,
                            waitlistCapacity = capacity,
                            additionalCampsiteIds = additionalCampsites.toList()
                        ).addOnSuccessListener { lottery ->
                            isSuccess = true

                            // Keep the in-memory Campsite objects in sync AFTER Firestore
                            // reports success. Collection<Lottery> has no add() method,
                            // so replace the field with a new collection containing the lottery.
                            lottery.campsiteIds.forEach { campsiteId ->
                                listOfNotNull(
                                    campsites[campsiteId],
                                    user.campsiteIds[campsiteId]
                                ).forEach { campsite ->
                                    val alreadyAdded = campsite.lotteryCollection.orEmpty()
                                        .any { it.id == lottery.id }
                                    if (!alreadyAdded) {
                                        campsite.lotteryCollection =
                                            campsite.lotteryCollection.orEmpty() + lottery
                                    }
                                }
                            }

                            Log.d("CREATE_LOTTERY", "Created lottery: ${lottery.id}")

                            // TODO: Replace the placeholder with your future booking
                            // schedule selection screen once that feature is implemented.
                            navController.navigate("bookingSchedule/${Uri.encode(lottery.id)}")

                        }.addOnFailureListener { exception ->
                            isSuccess = false
                            result = "Unable to create lottery: ${exception.message}"

                            Log.e(
                                "CREATE_LOTTERY",
                                "Lottery creation failed",
                                exception
                            )

                        }.addOnCompleteListener {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("createLotteryCreateButton")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("createLotteryLoadingIndicator"),
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                if (isLoading) "Creating Lottery..."
                else "Create Lottery"
            )
        }

        // Success/error feedback
        if (result.isNotEmpty()) {
            Text(
                text = result,
                color = if (isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("createLotteryResult")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Temporary booking schedule destination after a lottery is created.
 *
 * TODO: Implement booking schedule selection later.
 */
@Composable
fun BookingSchedulePlaceholderScreen(
    navController: NavController,
    lotteryId: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookingSchedulePlaceholderScreen"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TODO later",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
    }
}
