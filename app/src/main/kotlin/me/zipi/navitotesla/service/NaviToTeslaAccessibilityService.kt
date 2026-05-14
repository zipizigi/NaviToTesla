package me.zipi.navitotesla.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.R
import me.zipi.navitotesla.service.poifinder.NaverPoiFinder
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil

class NaviToTeslaAccessibilityService : AccessibilityService() {
    @Volatile private var lastCaptureAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedAt = System.currentTimeMillis()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connectedAt = 0L
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectedAt = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
                event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
            ) {
                return
            }
            if (!PoiFinderFactory.isNaverMap(event.packageName?.toString() ?: return)) return

            val now = System.currentTimeMillis()
            if (now - lastCaptureAt < CAPTURE_DEBOUNCE_MS) return
            lastCaptureAt = now

            val window = rootInActiveWindow ?: return
            val texts =
                window
                    .findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/route_search_bar")
                    ?.flatMap { collectTextsWithBounds(it) } ?: emptyList()
            destinationFrom(texts)?.let { NaverPoiFinder.addDestination(it) }
        } catch (e: Exception) {
            AnalysisUtil.warn("accessibility error: " + e.message)
            AnalysisUtil.recordException(e)
        }
    }

    override fun onInterrupt() {}

    private fun collectTextsWithBounds(node: AccessibilityNodeInfo): List<Pair<String, Rect>> {
        if (!node.isVisibleToUser) return emptyList()
        val result = mutableListOf<Pair<String, Rect>>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            result.add(it to rect)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result.addAll(collectTextsWithBounds(it)) }
        }
        return result
    }

    private fun destinationFrom(texts: List<Pair<String, Rect>>): String? {
        val anchorY = texts.firstOrNull { it.first == ENTRANCE_CHANGE_LABEL }?.second?.top
        val mainRows = if (anchorY != null) texts.filter { it.second.top < anchorY } else texts
        return mainRows.lastOrNull()?.first
    }

    companion object {
        private const val CAPTURE_DEBOUNCE_MS = 200L

        private const val ENTRANCE_CHANGE_LABEL = "출입구 변경"

        @Volatile private var connectedAt = 0L

        fun isAccessibilityServiceRunning(): Boolean = connectedAt > 0L

        private var lastNotifyAppVersion: String? = null

        /**
         * 내비게이션이 있고, 접근성이 필요하다면, 노티알림
         *
         * @param context     context
         * @param packageName packageName
         */
        fun notifyIfAvailable(
            context: Context,
            packageName: String,
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                if (!PoiFinderFactory.isNaverMap(packageName)) {
                    return@launch
                }
                if (!isAccessibilityServiceEnabled(context)) {
                    notifyRequireAccessibility(context)
                    return@launch
                }
                if (!isAccessibilityServiceRunning()) {
                    me.zipi.navitotesla.util.RelaunchNotifier.show(context)
                }
            }
        }

        private suspend fun notifyRequireAccessibility(context: Context) {
            val currentVersion = AppUpdaterUtil.getCurrentVersion(context)
            if (lastNotifyAppVersion == null) {
                lastNotifyAppVersion =
                    PreferencesUtil.getString("lastNotifyAppVersionForAccessibility")
            }
            if (lastNotifyAppVersion != null && lastNotifyAppVersion == currentVersion) {
                return
            }
            lastNotifyAppVersion = currentVersion
            PreferencesUtil.put("lastNotifyAppVersionForAccessibility", currentVersion)
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel =
                    NotificationChannel(
                        "notification_channel",
                        "Notification",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                notificationManager.createNotificationChannel(mChannel)
            }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent!!.putExtra("noti_action", "requireAccessibility")
            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, "notification_channel")
                    .setContentIntent(contentIntent)
                    .setContentTitle(context.getString(R.string.requireAccessibility))
                    .setContentText(context.getString(R.string.guideRequireAccessibility))
                    .setSmallIcon(R.drawable.ic_baseline_accessibility_new_24)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(2, notification)
        }

        fun isAccessibilityServiceEnabled(context: Context?): Boolean {
            if (context == null) {
                return false
            }
            val expected = "${context.packageName}/${NaviToTeslaAccessibilityService::class.java.name}"
            val enabled =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
            return enabled.split(':').any { it == expected }
        }
    }
}
