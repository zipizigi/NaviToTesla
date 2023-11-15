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
                // naver navi, 목적지 저장
                val goalList: MutableList<String> = ArrayList()
                // portrait
                val portrait =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/search_goal")
                // landscape
                val landscape =
                    rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/search_goal")
                goalList.addAll(parseNaverNaviDestination(portrait))
                goalList.addAll(parseNaverNaviDestination(landscape))
                if (goalList.size > 0) {
                    NaverPoiFinder.Companion.addDestination(goalList[0])
                }
            }
        } catch (e: Exception) {
            AnalysisUtil.warn("accessibility error: " + e.message)
            AnalysisUtil.recordException(e)
        }
    }

    override fun onInterrupt() {}
    private fun parseNaverNaviDestination(goalList: List<AccessibilityNodeInfo>?): List<String> {
        val result: MutableList<String> = ArrayList()
        if (goalList != null) {
            for (node in goalList) {
                if (node?.text != null && (node?.text?.toString()?.length ?: 0) > 0) {
                    result.add(node.text.toString())
                }
            }
        }
        return result
    }

    companion object {
        private var lastNotifyAppVersion: String? = null

        /**
         * 내비게이션이 있고, 접근성이 필요하다면, 노티알림
         *
         * @param context     context
         * @param packageName packageName
         */
        fun notifyIfAvailable(context: Context?, packageName: String) {
            // possible package
            if (!packageName.equals("com.nhn.android.nmap", ignoreCase = true)) {
                return
            }
            if (context == null) {
                return
            }
            if (isAccessibilityServiceEnabled(context)) {
                return
            }

            // AppUpdaterUtil.getCurrentVersion(this.getContext()
            val currentVersion = AppUpdaterUtil.getCurrentVersion(context)
            if (lastNotifyAppVersion == null) {
                lastNotifyAppVersion =
                    PreferencesUtil.getString(context, "lastNotifyAppVersionForAccessibility")
            }
            if (lastNotifyAppVersion != null && lastNotifyAppVersion == currentVersion) {
                return
            }
            lastNotifyAppVersion = currentVersion
            PreferencesUtil.put(context, "lastNotifyAppVersionForAccessibility", currentVersion)
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel = NotificationChannel(
                    "notification_channel", "Notification",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(mChannel)
            }
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent!!.putExtra("noti_action", "requireAccessibility")
            val contentIntent = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, "notification_channel")
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
            val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (enabledService in enabledServices) {
                val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
                if (enabledServiceInfo.packageName == context.packageName && enabledServiceInfo.name == NaviToTeslaAccessibilityService::class.java.name) return true
            }
            return false
        }
    }
}