package com.example.myapplication.frontEnd.screens.LotteryViews

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canEditLotteryListing
import com.example.myapplication.data.firebase.LotteryRepository.Companion.canViewLotteryWaitlist
import com.example.myapplication.core.datetime.AlbertaTime
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val lotteryInfoDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

/**
 * Renders lottery information prepared by repository queries.
 *
 * @param user Current profile, used for role and action access checks.
 * @param lotteries Records supplied by the personal, owned, or admin repository query.
 * @param personalJoinedOnly Uses the same personal membership presentation for every supported role.
 * @param campsites Canonical campsites used to verify ownership of shared lotteries.
 * @param onViewWaitlist Opens the selected lottery's guarded waitlist route.
 * @param onBack Returns to the previous screen.
 * @param loadError Optional repository listener failure; stale data remains visible with an error.
 * @param onEditLottery Opens the selected existing listing for an authorized owner or admin.
 * @param onLeaveLottery Removes the current entrant from the selected lottery and reports completion.
 * @param showAllActiveLotteries Admin-only list of all undrawn lotteries; the same layout is reused.
 */
@Composable
fun LotteryInformationScreen(
    user: User?,
    lotteries: List<Lottery>,
    campsites: Map<String, Campsite>,
    onViewWaitlist: (String) -> Unit,
    onBack: () -> Unit,
    loadError: String? = null,
    onEditLottery: ((String) -> Unit)? = null,
    onLeaveLottery: ((Lottery, (Boolean) -> Unit) -> Unit)? = null,
    showAllActiveLotteries: Boolean = false,
    personalJoinedOnly: Boolean = false
) {
    var leaveMessage by remember(user?.deviceID) { mutableStateOf<String?>(null) }
    val role = user?.userType?.trim()?.lowercase()
    val admin = role == "admin"
    val participant = !showAllActiveLotteries &&
        (personalJoinedOnly || role == "entrant" || admin)
    val listState = rememberLazyListState()
    LaunchedEffect(leaveMessage) {
        if (leaveMessage != null) listState.scrollToItem(0)
    }

    AdaptiveContent {
        Column(Modifier.fillMaxSize().padding(AppLayout.Medium), verticalArrangement = Arrangement.spacedBy(AppLayout.Small)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("lotteryInformationList"),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                item(key = "heading") {
                    Text(
                        when {
                            showAllActiveLotteries && admin -> "All active lotteries"
                            admin -> "My joined lotteries"
                            participant -> "Lotteries I've joined"
                            else -> "Lottery information"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("lotteryInformationTitle")
                    )
                }
                loadError?.let { message ->
                    item(key = "error") {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lotteryInformationError"))
                    }
                }
                leaveMessage?.let { message ->
                    item(key = "leave-feedback") { Text(message, modifier = Modifier.testTag("lotteryLeaveMessage")) }
                }
                when {
                    user == null -> item(key = "signed-out") {
                        Text("Sign in to view lottery information.", modifier = Modifier.testTag("lotteryInformationSignedOut"))
                    }
                    showAllActiveLotteries && !admin -> item(key = "access-denied") {
                        Text("Only admins can view all active lotteries.", modifier = Modifier.testTag("lotteryInformationDenied"))
                    }
                    lotteries.isEmpty() -> item(key = "empty") {
                        Text(
                            when {
                                showAllActiveLotteries -> "No active lotteries loaded."
                                participant -> "No joined lotteries loaded."
                                else -> "No lotteries for your campsites loaded."
                            },
                            modifier = Modifier.testTag("lotteryInformationEmpty")
                        )
                    }
                }

            // The controller supplies the list; this guard only controls the admin-only view.
            if (user != null && (!showAllActiveLotteries || admin)) {
                items(lotteries, key = { "lottery-${it.id}" }) { lottery ->
                    Card(Modifier.fillMaxWidth().testTag("lotteryInfo_${lottery.id}")) {
                        Column(
                            Modifier.padding(AppLayout.Medium),
                            verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
                        ) {
                            Text(
                                lottery.campsiteIds.joinToString().ifBlank { "Campsite unavailable" },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("lotteryCampsites_${lottery.id}")
                            )
                            Text(lotteryRegistrationStatus(lottery, LocalDateTime.now(AlbertaTime.zone)))
                            Text(
                                waitlistSummary(lottery),
                                modifier = Modifier.testTag("lotteryWaitlistCount_${lottery.id}")
                            )

                            if (lottery.campsiteIds.size > 1)
                                Text("These campsites share one waitlist.")

                            Text("Registration opens: ${lottery.period.registrationStart.format(lotteryInfoDateFormat)}")
                            Text("Registration ends: ${lottery.period.registrationEnd.format(lotteryInfoDateFormat)} (Alberta time)")
                            Text("Winners to select: ${lottery.numWinners}")

                            if (participant) {
                                Text(
                                    when {
                                        !lottery.drawn -> "You are signed up for this lottery."
                                        user!!.deviceID in lottery.winners -> "You were selected in this lottery."
                                        else -> "You were not selected in this lottery."
                                    },
                                    modifier = Modifier.testTag("lotteryEntrantStatus_${lottery.id}")
                                )
                            }

                            if (participant && onLeaveLottery != null) {
                                LeaveWaitlistButton(
                                    user,
                                    lottery,
                                    onLeave = { complete ->
                                        leaveMessage = null
                                        onLeaveLottery(lottery, complete)
                                    },
                                    onResult = { success ->
                                        leaveMessage =
                                            if (success) "You have left the waitlist for ${lottery.campsiteIds.joinToString()}."
                                            else "Could not leave the waitlist. It may have been drawn or changed. Please try again."
                                    }
                                )
                            }

                            if (!personalJoinedOnly && onEditLottery != null && canEditLotteryListing(user, lottery, campsites)) {
                                OutlinedButton(
                                    onClick = { onEditLottery(lottery.id) },
                                    modifier = Modifier.testTag("editLottery_${lottery.id}")
                                ) {
                                    Text("Edit listing")
                                }
                            }

                            if (!personalJoinedOnly && canViewLotteryWaitlist(user, lottery, campsites)) {
                                Button(
                                    onClick = { onViewWaitlist(lottery.id) },
                                    modifier = Modifier.testTag("viewWaitlist_${lottery.id}")
                                ) {
                                    Text("View waitlist")
                                }
                            }
                        }
                    }
                }
            }
            }

            Button(onClick = onBack, modifier = Modifier.testTag("lotteryInformationBack")) {
                Text("Back")
            }
        }
    }
}
