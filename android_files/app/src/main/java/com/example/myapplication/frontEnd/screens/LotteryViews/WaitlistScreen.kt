package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.profile.User
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canViewLotteryWaitlist
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canLeaveLotteryWaitlist
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

/**
 * Reusable read-only list of a lottery's current waitlist membership.
 * This component does not fetch data or grant access; its caller controls visibility.
 *
 * @param lottery Live lottery record; null shows an unavailable message.
 * @param onBack Returns to the previous screen.
 * @param showContactDetails Includes email and phone only when the caller has verified access.
 * Defaults to names only, for possible reuse in an entrant-facing view later.
 * @param loadError Optional listener error so retained data is not mistaken for a fresh server result.
 */
@Composable
fun WaitlistScreen(
    lottery: Lottery?,
    onBack: () -> Unit,
    showContactDetails: Boolean = false,
    loadError: String? = null
) {
    val entries = lottery?.waitlist.orEmpty().entries.sortedWith(
        compareBy({ it.value?.name?.lowercase().orEmpty() }, { it.key })
    )
    AdaptiveContent {
        Column(Modifier.fillMaxSize().padding(AppLayout.Medium), verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                item(key = "heading") {
                    Text("Lottery waitlist", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("waitlistTitle"))
                }
                loadError?.let { message ->
                    item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("waitlistLoadError")) }
                }
                item(key = "summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                        if (lottery == null) Text("This lottery is unavailable.", modifier = Modifier.testTag("waitlistUnavailable")) else {
                            Text(lottery.campsiteIds.joinToString(), modifier = Modifier.testTag("waitlistCampsites"))
                            Text(waitlistSummary(lottery), modifier = Modifier.testTag("waitlistCount"))
                            if (lottery.drawn) Text("Lottery drawn. Showing its recorded sign-ups.")
                            if (entries.isEmpty()) Text("No entrants have joined this waitlist.", modifier = Modifier.testTag("waitlistEmpty"))
                        }
                    }
                }
                items(entries, key = { "entrant-${it.key}" }) { entry ->
                    val profile = entry.value
                    Card(Modifier.fillMaxWidth().testTag("waitlistEntrant_${entry.key}")) {
                        Column(Modifier.padding(AppLayout.Medium), verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
                            Text(profile?.name?.takeIf { it.isNotBlank() } ?: "Entrant profile unavailable", modifier = Modifier.testTag("waitlistEntrantName_${entry.key}"))
                            if (showContactDetails && profile != null) {
                                Text("Email: ${profile.email.ifBlank { "Not provided" }}", modifier = Modifier.testTag("waitlistEntrantEmail_${entry.key}"))
                                Text("Phone: ${profile.phoneNumber.ifBlank { "Not provided" }}", modifier = Modifier.testTag("waitlistEntrantPhone_${entry.key}"))
                            }
                        }
                    }
                }
            }
            Button(onClick = onBack, modifier = Modifier.testTag("waitlistBack")) { Text("Back") }
        }
    }
}

/**
 * Checks the current profile before displaying owner/admin waitlist details.
 * Entrants cannot reach member details through this route, even with a known lottery ID.
 * Future public waitlist access must explicitly use [WaitlistScreen] without contact details.
 */
@Composable
fun LotteryWaitlistScreen(
    user: User?,
    lottery: Lottery?,
    campsites: Map<String, Campsite>,
    onBack: () -> Unit,
    loadError: String? = null
) {
    if (lottery != null && canViewLotteryWaitlist(user, lottery, campsites)) {
        WaitlistScreen(lottery, onBack, showContactDetails = true, loadError = loadError)
    } else {
        AdaptiveContent {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppLayout.Medium),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Medium)
            ) {
                Text(if (lottery == null) "This lottery is unavailable." else "You cannot view this lottery's waitlist.", modifier = Modifier.testTag("waitlistAccessMessage"))
                Button(onClick = onBack, modifier = Modifier.testTag("waitlistBack")) { Text("Back") }
            }
        }
    }
}

/**
 * Shared leave action for a joined, undrawn lottery. Prevents duplicate submissions.
 * @param onLeave Commits removal and invokes its callback with the result.
 * @param onResult Lets the parent keep feedback visible even after its lottery card disappears.
 * @param testTag Stable tag for either the campsite details or lottery information screen.
 */
@Composable
fun LeaveWaitlistButton(
    user: User?,
    lottery: Lottery,
    onLeave: ((Boolean) -> Unit) -> Unit,
    onResult: (Boolean) -> Unit,
    testTag: String = "leaveWaitlist_${lottery.id}"
) {
    if (!canLeaveLotteryWaitlist(user, lottery)) return
    var leaving by remember(user?.deviceID, lottery.id) { mutableStateOf(false) }
    var left by remember(user?.deviceID, lottery.id) { mutableStateOf(false) }
    OutlinedButton(enabled = !leaving && !left, modifier = Modifier.testTag(testTag), onClick = {
        if (leaving || left) return@OutlinedButton
        leaving = true
        onLeave { success ->
            leaving = false
            left = success
            onResult(success)
        }
    }) { Text(if (leaving) "Leaving..." else "Leave waitlist") }
}
