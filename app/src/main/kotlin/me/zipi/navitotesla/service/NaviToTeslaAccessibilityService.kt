package me.zipi.navitotesla.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil

class NaviToTeslaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED && event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED) {
                return
            }
            if (event.packageName == "com.nhn.android.nmap") {
                val window = rootInActiveWindow ?: return
                // route_search_bar: Compose 기반 경로 검색 바 (출발지, 목적지 순서로 TextView 포함)
                val searchBarNodes = window.findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/route_search_bar")
                val texts = mutableListOf<String>()
                searchBarNodes?.forEach { node -> collectTexts(node, texts) }
                // 마지막 텍스트가 목적지
                texts.lastOrNull()?.let { NaverPoiFinder.addDestination(it) }
            }
        } catch (e: Exception) {
            AnalysisUtil.warn("accessibility error: " + e.message)
            AnalysisUtil.recordException(e)
        }
    }

    override fun onInterrupt() {}

    private fun collectTexts(
        node: AccessibilityNodeInfo,
        result: MutableList<String>,
    ) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) result.add(text)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, result) }
        }
    }

    companion object {
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
                // possible package
                if (!packageName.equals("com.nhn.android.nmap", ignoreCase = true)) {
                    return@launch
                }
                if (isAccessibilityServiceEnabled(context)) {
                    return@launch
                }

                // AppUpdaterUtil.getCurrentVersion(this.getContext()
                val currentVersion = AppUpdaterUtil.getCurrentVersion(context)
                if (lastNotifyAppVersion == null) {
                    lastNotifyAppVersion =
                        PreferencesUtil.getString("lastNotifyAppVersionForAccessibility")
                }
                if (lastNotifyAppVersion != null && lastNotifyAppVersion == currentVersion) {
                    return@launch
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
        }

        fun isAccessibilityServiceEnabled(context: Context?): Boolean {
            if (context == null) {
                return false
            }
            val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (enabledService in enabledServices) {
                val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
                if (enabledServiceInfo.packageName == context.packageName &&
                    enabledServiceInfo.name == NaviToTeslaAccessibilityService::class.java.name
                ) {
                    return true
                }
            }
            return false
        }
    }
}
