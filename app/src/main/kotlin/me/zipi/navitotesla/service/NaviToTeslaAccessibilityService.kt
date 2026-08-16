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
import me.zipi.navitotesla.service.poifinder.KakaoPoiFinder
import me.zipi.navitotesla.service.poifinder.NaverPoiFinder
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil

class NaviToTeslaAccessibilityService : AccessibilityService() {
    @Volatile private var lastCaptureAt = 0L

    @Volatile private var lastKakaoScanAt = 0L

    @Volatile private var kakaoScanUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedAt = System.currentTimeMillis()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connectedAt = 0L
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectedAt = 0L
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            when {
                PoiFinderFactory.isNaverMap(packageName) -> onNaverEvent(event)
                PoiFinderFactory.isKakaoNavi(packageName) -> onKakaoEvent(event)
            }
        } catch (e: Exception) {
            AnalysisUtil.warn("accessibility error: " + e.message)
            AnalysisUtil.recordException(e)
        }
    }

    private fun onNaverEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCaptureAt < CAPTURE_DEBOUNCE_MS) return
        lastCaptureAt = now

        val window = rootInActiveWindow ?: return
        val texts =
            window
                .findAccessibilityNodeInfosByViewId(NAVER_SEARCH_BAR_ID)
                ?.flatMap { collectTextsWithBounds(it) } ?: emptyList()
        destinationFrom(texts)?.let { NaverPoiFinder.addDestination(it) }
    }

    /**
     * 카카오는 주행 화면이 콘텐츠 변경 이벤트를 계속 쏟아낸다.
     * 화면이 바뀐 뒤 잠깐 열리는 스캔 창 안에서만 트리를 훑는다.
     */
    private fun onKakaoEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        when {
            windowChanged -> kakaoScanUntil = now + KAKAO_SCAN_WINDOW_MS
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (now > kakaoScanUntil) return
            else -> return
        }
        if (now - lastKakaoScanAt < CAPTURE_DEBOUNCE_MS) return
        lastKakaoScanAt = now

        val root = rootInActiveWindow ?: return
        val scoped = root.findAccessibilityNodeInfosByViewId(KAKAO_ROUTE_SUMMARY_ID)
        // 전체 트리 탐색은 화면이 막 바뀐 순간에만 한다. 주행 화면 갱신마다 훑으면 비싸다.
        val roots =
            if (!scoped.isNullOrEmpty()) {
                scoped
            } else if (windowChanged) {
                listOf(root)
            } else {
                return
            }
        for (node in roots) {
            findKakaoDestination(node, intArrayOf(0), 0)?.let {
                KakaoPoiFinder.addDestination(it)
                // 앵커가 보이는 동안은 계속 갱신되도록 창을 연장한다.
                kakaoScanUntil = now + KAKAO_SCAN_WINDOW_MS
                return
            }
        }
    }

    private fun findKakaoDestination(
        node: AccessibilityNodeInfo?,
        visited: IntArray,
        depth: Int,
    ): String? {
        if (node == null || depth > MAX_DEPTH || visited[0] > MAX_NODES) return null
        visited[0]++
        node.contentDescription?.toString()?.let { description ->
            KAKAO_ROUTE_ANCHOR.find(description)?.let { return it.groupValues[1] }
        }
        for (i in 0 until node.childCount) {
            findKakaoDestination(node.getChild(i), visited, depth + 1)?.let { return it }
        }
        return null
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

        private const val NAVER_SEARCH_BAR_ID = "com.nhn.android.nmap:id/route_search_bar"
        private const val KAKAO_ROUTE_SUMMARY_ID = "com.locnall.KimGiSa:id/route_result_trip"

        private val KAKAO_ROUTE_ANCHOR = Regex("^출발\\s+.+?,\\s*도착\\s+(.+)$")

        private const val KAKAO_SCAN_WINDOW_MS = 20_000L
        private const val MAX_DEPTH = 40
        private const val MAX_NODES = 3000

        @Volatile private var connectedAt = 0L

        @Volatile private var instance: NaviToTeslaAccessibilityService? = null

        fun isAccessibilityServiceRunning(): Boolean = connectedAt > 0L

        /** 안내가 시작되면 더 훑을 이유가 없다. */
        fun closeScanWindow() {
            instance?.kakaoScanUntil = 0L
        }

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
            notificationText: String,
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                if (!PoiFinderFactory.isAccessibilityRequired(packageName, notificationText)) {
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
