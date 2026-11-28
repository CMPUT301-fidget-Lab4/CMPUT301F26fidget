package com.example.myapplication.features.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R

private const val LOTTERY_RESULTS_CHANNEL_ID = "lottery_results"

internal class AndroidLotteryNotificationPresenter(
    private val manager: NotificationManager,
    private val canPost: () -> Boolean,
    private val buildNotification: (AppNotification) -> Notification
) {
    fun post(id: String, notification: AppNotification): Boolean {
        if (!canPost()) return false
        manager.notify(id, 0, buildNotification(notification))
        return true
    }

    companion object {
        fun create(context: Context): AndroidLotteryNotificationPresenter? {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return null
            manager.createNotificationChannel(
                NotificationChannel(LOTTERY_RESULTS_CHANNEL_ID, "Lottery results", NotificationManager.IMPORTANCE_HIGH)
            )
            return AndroidLotteryNotificationPresenter(
                manager,
                canPost = {
                    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    val channel = manager.getNotificationChannel(LOTTERY_RESULTS_CHANNEL_ID)
                    permissionGranted && manager.areNotificationsEnabled() &&
                        channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
                },
                buildNotification = { notification ->
                    NotificationCompat.Builder(context, LOTTERY_RESULTS_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(if (notification.type == "WIN") "You won!" else "Lottery result")
                        .setContentText(notification.message)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .build()
                }
            )
        }
    }
}
