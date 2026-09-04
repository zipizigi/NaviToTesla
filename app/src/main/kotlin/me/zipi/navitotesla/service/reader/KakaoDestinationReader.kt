package me.zipi.navitotesla.service.reader

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zipi.navitotesla.service.poifinder.KakaoPoiFinder
import me.zipi.navitotesla.service.poifinder.NaviDriveMode

/** 경로요약 화면의 `출발 …, 도착 {목적지}` contentDescription 에서 읽는다. */
class KakaoDestinationReader : DestinationReader {
    @Volatile private var lastScanAt = 0L

    @Volatile private var scanUntil = 0L

    override fun onEvent(
        event: AccessibilityEvent,
        root: () -> AccessibilityNodeInfo?,
    ) {
        val now = System.currentTimeMillis()
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        when {
            windowChanged -> {
                scanUntil = now + DestinationReader.SCAN_WINDOW_MS
            }

            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (now > scanUntil) return
            }

            else -> {
                return
            }
        }
        if (now - lastScanAt < DestinationReader.DEBOUNCE_MS) return
        lastScanAt = now

        val node = root() ?: return
        val scoped = node.findAccessibilityNodeInfosByViewId(ROUTE_SUMMARY_ID)
        val roots =
            when {
                !scoped.isNullOrEmpty() -> scoped
                windowChanged -> listOf(node)
                else -> return
            }
        for (candidate in roots) {
            val dest =
                A11yNodes.findFirst(candidate) { n ->
                    n.contentDescription?.toString()?.let { ROUTE_ANCHOR.find(it)?.groupValues?.get(1) }
                }
            if (dest != null) {
                KakaoPoiFinder.addDestination(dest)
                scanUntil = now + DestinationReader.SCAN_WINDOW_MS
                return
            }
        }
    }

    override fun closeScanWindow() {
        scanUntil = 0L
    }

    override fun driveMode(root: () -> AccessibilityNodeInfo?): NaviDriveMode =
        DestinationReader.pollMode(root) { node ->
            val labels = A11yNodes.labels(node)
            when {
                labels.any { l -> SAFE_MARKERS.any { it in l } } -> NaviDriveMode.SAFE_DRIVE
                labels.any { l -> GUIDANCE_MARKERS.any { it in l } } -> NaviDriveMode.GUIDANCE
                else -> NaviDriveMode.UNKNOWN
            }
        }

    private companion object {
        const val ROUTE_SUMMARY_ID = "com.locnall.KimGiSa:id/route_result_trip"
        val ROUTE_ANCHOR = Regex("^출발\\s+.+?,\\s*도착\\s+(.+)$")

        // 449 리소스 실측으로 검증한 문구만. 부분문자열 매칭이라 길안내 화면 문구와 겹치면 안 된다.
        // "교통상황" 은 재탐색 토스트("교통상황이 변하여 …")와 겹쳐 길안내를 안전운전으로 오판시켰다.
        // "신호등 정보" 와 "경로 새로고침" 은 449 리소스에 없는 죽은 마커였다.
        val SAFE_MARKERS = listOf("안전운전 종료")
        val GUIDANCE_MARKERS = listOf("전체경로", "남음")
    }
}
