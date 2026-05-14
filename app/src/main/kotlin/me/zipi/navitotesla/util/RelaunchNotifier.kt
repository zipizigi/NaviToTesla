package me.zipi.navitotesla.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import me.zipi.navitotesla.R

object RelaunchNotifier {
    const val NOTIFICATION_ID = 3
    const val NOTI_ACTION_VALUE = "requireRelaunch"
    private const val CHANNEL_ID = "notification_channel"
    private const val PENDING_INTENT_REQUEST_CODE = 3

    fun show(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Notification",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            notificationManager.createNotificationChannel(channel)
        }
        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("noti_action", NOTI_ACTION_VALUE)
                }
        val contentIntent =
            PendingIntent.getActivity(
                context,
                PENDING_INTENT_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.guideRequireRelaunch))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.guideRequireRelaunch)))
                .setSmallIcon(R.drawable.ic_baseline_accessibility_new_24)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
