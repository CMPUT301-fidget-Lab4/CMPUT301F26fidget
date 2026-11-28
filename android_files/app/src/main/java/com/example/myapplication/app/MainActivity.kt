package com.example.myapplication.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings.Secure
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.frontEnd.theme.MyApplicationTheme
import com.example.myapplication.frontEnd.viewcontroller.CampsiteApp
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.data.firebase.CampsiteRepository
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.lottery.LotteryDrawOutcome
import com.example.myapplication.features.notification.AndroidLotteryNotificationPresenter
import com.example.myapplication.data.firebase.NotificationRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.data.firebase.UserRepository
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.data.firebase.FeedbackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val LOTTERY_RETRY_DELAY_MS = 30_000L
private const val NOTIFICATION_PERMISSION_REQUESTED = "notificationPermissionRequested"

internal class LotteryNotificationViewModel(
    val repository: NotificationRepository = NotificationRepository()
) : ViewModel()

class MainActivity : ComponentActivity() {
    private var notificationPermissionRequested = false
    private val notificationPermissionGrants = MutableStateFlow(0)
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) notificationPermissionGrants.value++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationPermissionRequested =
            savedInstanceState?.getBoolean(NOTIFICATION_PERMISSION_REQUESTED) ?: false

        @SuppressLint("HardwareIds")
        val currentDeviceID: String = Secure.getString(this.contentResolver, Secure.ANDROID_ID)
        val scheduleRepository = BookingScheduleRepository()
        val reservationRepository = ReservationRepository(scheduleRepository)
        val userRepository = UserRepository(currentDeviceID)
        val notificationRepository = ViewModelProvider(this)[LotteryNotificationViewModel::class.java].repository
        val messageRepository = MessageRepository()
        val feedbackRepository = FeedbackRepository()
        lateinit var campsiteRepository: CampsiteRepository
        val lotteryRepository = LotteryRepository(campsiteLookup = { campsiteRepository.campsitesMap[it] }, userLookup = userRepository::findUser)
        campsiteRepository = CampsiteRepository(sharedLotteryRepository = lotteryRepository)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                campsiteRepository.close()
                userRepository.close()
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { notificationRepository.deliverLotteryResults() }
                launch {
                    snapshotFlow {
                        userRepository.currentUser?.let { it.deviceID to it.lotteryAlertsEnabled }
                    }
                        .combine(notificationPermissionGrants) { profile, _ -> profile }
                        .collectLatest { profile ->
                            val presenter = if (profile?.second == true) {
                                requestNotificationPermissionIfNeeded()
                                AndroidLotteryNotificationPresenter.create(applicationContext)
                            } else null
                            notificationRepository.observeInbox(profile?.first, presenter?.let { it::post })
                        }
                }
                launch {
                    snapshotFlow { userRepository.users.toList() }.collect {
                        lotteryRepository.refreshReferences()
                    }
                }
                launch {
                    runForegroundLotteryChecks(lotteryRepository) {
                        campsiteRepository.campsitesMap.filterValues { it.inLottery }.keys.toSet()
                    }
                }
            }
        }

        setContent {
            MyApplicationTheme {
                CampsiteApp(
                    user = userRepository.currentUser,
                    users = userRepository.users,
                    onAddUser = userRepository::addUser,
                    onDeleteUser = userRepository::deleteUser,
                    onUpdateCampsite = campsiteRepository::updateCampsiteDetails,
                    onUpdateUser = userRepository::updateUser,
                    findUser = userRepository::findUser,
                    getJoinedLotteries = userRepository::getJoinedLotteries,
                    getAllActiveLotteryWaitlists = userRepository::getAllActiveLotteryWaitlists,
                    getLotteryWaitlist = userRepository::getLotteryWaitlist,
                    getAndroidId = userRepository::getDeviceID,
                    authState = userRepository.authState,
                    campsites = campsiteRepository.campsitesMap,
                    findCampsite = campsiteRepository::findCampsite,
                    onAddCampsite = campsiteRepository::addCampsite,
                    reservationRepository = reservationRepository,
                    scheduleRepository = scheduleRepository,
                    lotteryRepository = lotteryRepository,
                    notificationRepository = notificationRepository,
                    messageRepository = messageRepository,
                    feedbackRepository = feedbackRepository,
                    bookingSchedules = campsiteRepository.bookingSchedules,
                    retryBookingSchedules = campsiteRepository::retryBookingSchedules
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(NOTIFICATION_PERMISSION_REQUESTED, notificationPermissionRequested)
        super.onSaveInstanceState(outState)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionRequested &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequested = true
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private suspend fun runForegroundLotteryChecks(
    repository: LotteryRepository,
    activeCampsiteIds: () -> Set<String>
): Unit = coroutineScope {
    val changes = Channel<Unit>(Channel.CONFLATED)
    val lotteryObserver = repository.observeLotteries { changes.trySend(Unit) }
    val campsiteObserver = launch {
        snapshotFlow { activeCampsiteIds() to repository.listenerError }.collect { changes.trySend(Unit) }
    }

    try {
        while (isActive) {
            repository.startListening()
            val active = activeCampsiteIds()
            val nextDraw = repository.timeUntilNextDrawMillis(active)
            if (nextDraw != 0L) {
                val listenerRetry = LOTTERY_RETRY_DELAY_MS.takeIf { repository.listenerError != null }
                val wait = listOfNotNull(nextDraw, listenerRetry).minOrNull()
                if (wait == null) changes.receive()
                else withTimeoutOrNull(wait) { changes.receive() }
                continue
            }

            changes.tryReceive()
            val progress = try {
                val outcomes = repository.drawClosedLotteries(active).await()
                outcomes.forEach { outcome ->
                    when (outcome) {
                        is LotteryDrawOutcome.Committed ->
                            Log.i("LotteryAutoDraw", "Drew lottery ${outcome.lotteryId}")
                        is LotteryDrawOutcome.Failed ->
                            Log.e("LotteryAutoDraw", "Could not draw lottery ${outcome.lotteryId}", outcome.error)
                        is LotteryDrawOutcome.Repaired, is LotteryDrawOutcome.Skipped -> Unit
                    }
                }
                outcomes.any { it is LotteryDrawOutcome.Committed || it is LotteryDrawOutcome.Repaired }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("LotteryAutoDraw", "Could not check closed lotteries", error)
                false
            }
            // Back off if the transaction disagrees with the currently observed candidate.
            if (progress) withTimeoutOrNull(LOTTERY_RETRY_DELAY_MS) { changes.receive() }
            else delay(LOTTERY_RETRY_DELAY_MS)
        }
    } finally {
        lotteryObserver.remove()
        campsiteObserver.cancel()
        changes.close()
    }
}
