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
import me.zipi.navitotesla.service.poifinder.NaviDriveMode
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil

class NaviToTeslaAccessibilityService : AccessibilityService() {
    @Volatile private var lastCaptureAt = 0L

    @Volatile private var lastKakaoScanAt = 0L

    @Volatile private var kakaoScanUntil = 0L

    @Volatile private var naverScanUntil = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedAt = System.currentTimeMillis()
        instance = this
        KakaoPoiFinder.setDriveModeProvider { kakaoDriveMode() }
        NaverPoiFinder.setDriveModeProvider { naverDriveMode() }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connectedAt = 0L
        instance = null
        KakaoPoiFinder.setDriveModeProvider(null)
        NaverPoiFinder.setDriveModeProvider(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectedAt = 0L
        instance = null
        KakaoPoiFinder.setDriveModeProvider(null)
        NaverPoiFinder.setDriveModeProvider(null)
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

    /**
     * 클릭만 보면 자동 안내 시작이나 사용자가 아무것도 누르지 않은 경로 화면을 놓친다.
     * 기존 클릭 경로는 그대로 두고 화면 변경도 함께 본다.
     */
    private fun onNaverEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            -> naverScanUntil = now + NAVER_SCAN_WINDOW_MS

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (now > naverScanUntil) return

            else -> return
        }
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

    /**
     * 주행 화면이 길안내인지 안전운전인지 화면에서 가른다.
     * 알림은 두 모드 모두 `길안내 주행 중` 으로 뜨기 때문에 알림만으로는 구분되지 않는다.
     * 화면이 아직 안 그려졌을 수 있어 잠깐 기다린다 — 호출부는 WorkManager 라 블로킹해도 된다.
     */
    private fun kakaoDriveMode(): NaviDriveMode {
        val deadline = System.currentTimeMillis() + DRIVE_MODE_TIMEOUT_MS
        while (true) {
            val root = rootInActiveWindow
            if (root != null) {
                val markers = mutableSetOf<String>()
                collectMarkers(root, markers, intArrayOf(0), 0)
                when {
                    markers.any { m -> KAKAO_SAFE_MARKERS.any { it in m } } -> return NaviDriveMode.SAFE_DRIVE
                    markers.any { m -> KAKAO_GUIDANCE_MARKERS.any { it in m } } -> return NaviDriveMode.GUIDANCE
                }
            }
            if (System.currentTimeMillis() >= deadline) return NaviDriveMode.UNKNOWN
            Thread.sleep(DRIVE_MODE_POLL_MS)
        }
    }

    /**
     * 네이버도 안전운행에서 길안내와 같은 알림(`내비게이션 - 안내 중`)을 띄운다.
     * viewId 로 갈리므로 문자열과 달리 언어의 영향을 받지 않는다.
     */
    private fun naverDriveMode(): NaviDriveMode {
        val deadline = System.currentTimeMillis() + DRIVE_MODE_TIMEOUT_MS
        while (true) {
            val root = rootInActiveWindow
            if (root != null) {
                if (NAVER_SAFE_IDS.any { !root.findAccessibilityNodeInfosByViewId(it).isNullOrEmpty() }) {
                    return NaviDriveMode.SAFE_DRIVE
                }
                if (NAVER_GUIDE_IDS.any { !root.findAccessibilityNodeInfosByViewId(it).isNullOrEmpty() }) {
                    return NaviDriveMode.GUIDANCE
                }
            }
            if (System.currentTimeMillis() >= deadline) return NaviDriveMode.UNKNOWN
            Thread.sleep(DRIVE_MODE_POLL_MS)
        }
    }

    private fun collectMarkers(
        node: AccessibilityNodeInfo?,
        out: MutableSet<String>,
        visited: IntArray,
        depth: Int,
    ) {
        if (node == null || depth > MAX_DEPTH || visited[0] > MAX_NODES) return
        visited[0]++
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            collectMarkers(node.getChild(i), out, visited, depth + 1)
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
        val anchorY = texts.firstOrNull { it.first in ENTRANCE_CHANGE_LABELS }?.second?.top
        val mainRows = if (anchorY != null) texts.filter { it.second.top < anchorY } else texts
        return mainRows.lastOrNull()?.first
    }

    companion object {
        private const val CAPTURE_DEBOUNCE_MS = 200L

        /**
         * 네이버 `map_navi_change_entrance2`. 로케일 디렉토리는 171개지만
         * 이 문자열이 실제로 번역된 건 아래 4개뿐이고 나머지는 영어로 폴백한다.
         */
        private val ENTRANCE_CHANGE_LABELS =
            setOf(
                "출입구 변경",
                "Entrance",
                "出入口変更",
                "变更出入口",
            )

        private const val NAVER_SEARCH_BAR_ID = "com.nhn.android.nmap:id/route_search_bar"

        /** 안전운행엔 안내종료 버튼만, 길안내엔 ETA 행이 있다. */
        private val NAVER_SAFE_IDS = listOf("com.nhn.android.nmap:id/v_quit")
        private val NAVER_GUIDE_IDS =
            listOf(
                "com.nhn.android.nmap:id/eta",
                "com.nhn.android.nmap:id/duration",
                "com.nhn.android.nmap:id/tv_first_distance",
            )

        private const val NAVER_SCAN_WINDOW_MS = 20_000L
        private const val KAKAO_ROUTE_SUMMARY_ID = "com.locnall.KimGiSa:id/route_result_trip"

        private val KAKAO_ROUTE_ANCHOR = Regex("^출발\\s+.+?,\\s*도착\\s+(.+)$")

        /** 카카오는 언어별 리소스가 없어 한국어 문자열이 로케일과 무관하게 유지된다. */
        private val KAKAO_SAFE_MARKERS = listOf("안전운전 종료", "교통상황", "신호등 정보")
        private val KAKAO_GUIDANCE_MARKERS = listOf("전체경로", "경로 새로고침", "남음")

        private const val DRIVE_MODE_TIMEOUT_MS = 2_500L
        private const val DRIVE_MODE_POLL_MS = 200L

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
