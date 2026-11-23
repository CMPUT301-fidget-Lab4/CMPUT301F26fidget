package com.example.myapplication.backEnd

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.backEnd.featureModels.AppNotification
import com.example.myapplication.backEnd.firebaseRepositories.CampsiteRepository
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.NotificationRepository
import com.example.myapplication.backEnd.firebaseRepositories.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

private const val LOTTERY_CHECK_INTERVAL_MS = 60_000L
private const val LOTTERY_LOG_TAG = "LotteryAutoDraw"
private const val NOTIFICATION_CHANNEL_ID = "lottery_results"

/** Owns app-wide notifications, reference updates, lottery checks, and repository cleanup. */
@Composable
fun AppEffects(
    userRepository: UserRepository,
    campsiteRepository: CampsiteRepository,
    lotteryRepository: LotteryRepository,
    notificationRepository: NotificationRepository
) {
    val user = userRepository.currentUser
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(user?.deviceID) {
        if (user != null && Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(user?.deviceID, notificationRepository, context) {
        val registration = user?.let { currentUser ->
            notificationRepository.listenForUnread(currentUser.deviceID) { notification ->
                showNotification(context, notification)
                notificationRepository.markRead(notification.id)
            }
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(lotteryRepository, userRepository) {
        snapshotFlow { userRepository.users.toList() }.collect {
            lotteryRepository.refreshReferences()
        }
    }

    DisposableEffect(campsiteRepository) {
        onDispose { campsiteRepository.close() }
    }

    LaunchedEffect(lotteryRepository) {
        // Wait for each check to finish before starting the one-minute interval.
        while (true) {
            checkClosedLotteries(lotteryRepository)
            delay(LOTTERY_CHECK_INTERVAL_MS)
        }
    }
}

/** Displays a lottery result through the shared Android notification channel. */
private fun showNotification(context: Context, notification: AppNotification) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(NOTIFICATION_CHANNEL_ID, "Lottery results", NotificationManager.IMPORTANCE_HIGH)
    )
    val systemNotification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(if (notification.type == "WIN") "You won!" else "Lottery result")
        .setContentText(notification.message)
        .setAutoCancel(true)
        .build()
    manager.notify(notification.id.hashCode(), systemNotification)
}

private suspend fun checkClosedLotteries(lotteryRepository: LotteryRepository) {
    val finished = CompletableDeferred<Unit>()
    lotteryRepository.drawClosedLotteries().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val newlyDrawn = task.result
            if (newlyDrawn.isNotEmpty()) {
                Log.i(LOTTERY_LOG_TAG, "Drew lotteries: $newlyDrawn")
            }
        } else {
            Log.e(LOTTERY_LOG_TAG, "Could not check closed lotteries", task.exception)
        }
        finished.complete(Unit)
    }
    finished.await()
}
