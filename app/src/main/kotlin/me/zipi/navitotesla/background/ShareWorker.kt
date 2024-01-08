package me.zipi.navitotesla.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import me.zipi.navitotesla.R
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil

class ShareWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val naviToTeslaService: NaviToTeslaService
    private val channelId = "location_share_channel"

    init {
        naviToTeslaService = NaviToTeslaService(context)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(1, createNotification())
    }

    override suspend fun doWork(): Result {
        AnalysisUtil.log("Start share worker")
        val inputData = inputData
        val packageName = inputData.getString("packageName")!!
        val notificationTitle = inputData.getString("notificationTitle")
        val notificationText = inputData.getString("notificationText")
        naviToTeslaService.share(packageName, notificationTitle, notificationText)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // create channel
        val mChannel = NotificationChannel(channelId, "Share notification", NotificationManager.IMPORTANCE_LOW)
        mChannel.setSound(null, null)
        mChannel.setShowBadge(false)
        mChannel.description = "차량에 위치를 공유할 때 알림이 나타납니다."
        mChannel.vibrationPattern = longArrayOf(0L)
        notificationManager.createNotificationChannel(mChannel)
    }

    private fun createNotification(): Notification {
        val context = applicationContext
        val packageName = inputData.getString("packageName")
        val notificationText = inputData.getString("notificationText")

        createNotificationChannel()

        var poiName: String? = ""
        var address = ""
        if (packageName != null) {
            val poiFinder = PoiFinderFactory.getPoiFinder(packageName)
            address = poiFinder.parseDestination(notificationText ?: "")
            if (!naviToTeslaService.isAddress(address)) {
                poiName = address
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId).setContentIntent(contentIntent)
            .setContentText(context.getString(R.string.sendingDestination) + "\n" + address)
            .setTicker(context.getString(R.string.sendingDestination) + "\n" + poiName).setSmallIcon(R.drawable.ic_baseline_share_24)
            .setAutoCancel(true).setVibrate(longArrayOf(0L)).setSound(null).build()
    }

    companion object {
        fun startShare(
            context: Context,
            packageName: String,
            notificationTitle: String?,
            notificationText: String?,
        ) {
            AnalysisUtil.log("Register share worker")
            val workRequest: WorkRequest = OneTimeWorkRequestBuilder<ShareWorker>()//(ShareWorker::class.java)
                .setInputData(
                    Data.Builder().putString("packageName", packageName).putString("notificationTitle", notificationTitle)
                        .putString("notificationText", notificationText).build(),
                ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
