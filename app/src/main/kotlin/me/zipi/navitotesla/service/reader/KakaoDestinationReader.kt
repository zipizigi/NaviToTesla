package me.zipi.navitotesla.service.reader

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zipi.navitotesla.service.poifinder.KakaoPoiFinder
import me.zipi.navitotesla.service.poifinder.NaviDriveMode

/**
 * 카카오내비는 경로요약 화면의 `출발 …, 도착 {목적지}` contentDescription 에서 읽는다.
 * 주행 화면이 콘텐츠 변경 이벤트를 계속 쏟아내므로 스캔 창이 열려 있을 때만 훑는다.
 */
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
        // 전체 트리 탐색은 화면이 막 바뀐 순간에만. 주행 화면 갱신마다 훑으면 비싸다.
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
                // 앵커가 보이는 동안 계속 갱신되도록 창을 연장한다.
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

        /** 카카오는 언어별 리소스가 없어 한국어 문자열이 로케일과 무관하게 유지된다. */
        val SAFE_MARKERS = listOf("안전운전 종료", "교통상황", "신호등 정보")
        val GUIDANCE_MARKERS = listOf("전체경로", "경로 새로고침", "남음")
    }
}
