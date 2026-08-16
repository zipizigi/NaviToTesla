package me.zipi.navitotesla.service.reader

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zipi.navitotesla.service.poifinder.NaverPoiFinder
import me.zipi.navitotesla.service.poifinder.NaviDriveMode

/**
 * 네이버지도는 경로 결과 화면 `route_search_bar` 안의 텍스트에서 읽는다.
 * 클릭만 보면 자동 안내 시작을 놓치므로 화면 변경도 함께 본다.
 */
class NaverDestinationReader : DestinationReader {
    @Volatile private var lastScanAt = 0L

    @Volatile private var scanUntil = 0L

    override fun onEvent(
        event: AccessibilityEvent,
        root: () -> AccessibilityNodeInfo?,
    ) {
        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            -> scanUntil = now + DestinationReader.SCAN_WINDOW_MS

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (now > scanUntil) return

            else -> return
        }
        if (now - lastScanAt < DestinationReader.DEBOUNCE_MS) return
        lastScanAt = now

        val node = root() ?: return
        val texts =
            node
                .findAccessibilityNodeInfosByViewId(SEARCH_BAR_ID)
                ?.flatMap { A11yNodes.visibleTexts(it) } ?: emptyList()
        destinationFrom(texts)?.let { NaverPoiFinder.addDestination(it) }
    }

    /** 출입구 행은 목적지 아래에 붙는다. 앵커 위쪽 마지막 줄이 목적지다. */
    private fun destinationFrom(texts: List<Pair<String, android.graphics.Rect>>): String? {
        val anchorY = texts.firstOrNull { it.first in ENTRANCE_CHANGE_LABELS }?.second?.top
        val rows = if (anchorY != null) texts.filter { it.second.top < anchorY } else texts
        return rows.lastOrNull()?.first
    }

    override fun closeScanWindow() {
        scanUntil = 0L
    }

    override fun driveMode(root: () -> AccessibilityNodeInfo?): NaviDriveMode =
        DestinationReader.pollMode(root) { node ->
            when {
                SAFE_IDS.any { A11yNodes.hasViewId(node, it) } -> NaviDriveMode.SAFE_DRIVE
                GUIDE_IDS.any { A11yNodes.hasViewId(node, it) } -> NaviDriveMode.GUIDANCE
                else -> NaviDriveMode.UNKNOWN
            }
        }

    private companion object {
        const val PKG = "com.nhn.android.nmap"
        const val SEARCH_BAR_ID = "$PKG:id/route_search_bar"

        /**
         * 네이버 `map_navi_change_entrance2`. 로케일 디렉토리는 171개지만
         * 이 문자열이 실제로 번역된 건 아래 4개뿐이고 나머지는 영어로 폴백한다.
         */
        val ENTRANCE_CHANGE_LABELS = setOf("출입구 변경", "Entrance", "出入口変更", "变更出入口")

        /** 안전운행엔 안내종료 버튼만, 길안내엔 ETA 행이 있다. viewId 라 언어와 무관하다. */
        val SAFE_IDS = listOf("$PKG:id/v_quit")
        val GUIDE_IDS = listOf("$PKG:id/eta", "$PKG:id/duration", "$PKG:id/tv_first_distance")
    }
}
