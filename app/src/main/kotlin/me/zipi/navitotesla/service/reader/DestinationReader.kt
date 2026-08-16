package me.zipi.navitotesla.service.reader

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zipi.navitotesla.service.poifinder.NaviDriveMode

/**
 * 접근성 트리에서 목적지를 읽어 온다. 화면을 이미지로 캡처하는 게 아니라
 * 노드의 text/contentDescription 을 읽는다.
 *
 * 앱마다 화면 구조도 관심 이벤트도 달라 `PoiFinder` 처럼 앱별로 나눠 둔다.
 * `root` 는 매번 새로 얻어야 하므로 값이 아니라 공급자로 받는다.
 */
interface DestinationReader {
    /** 이벤트를 받아 필요하면 화면을 훑고 목적지를 저장한다. */
    fun onEvent(
        event: AccessibilityEvent,
        root: () -> AccessibilityNodeInfo?,
    )

    /**
     * 주행 화면이 길안내인지 안전운전인지.
     * 두 앱 모두 안전운전에서도 길안내와 같은 알림을 띄우기 때문에 화면을 봐야 갈린다.
     */
    fun driveMode(root: () -> AccessibilityNodeInfo?): NaviDriveMode

    /** 안내가 시작되면 더 훑을 이유가 없다. */
    fun closeScanWindow()

    companion object {
        const val DEBOUNCE_MS = 200L

        /** 화면 전환 후 이 시간 동안만 콘텐츠 변경 이벤트를 훑는다. */
        const val SCAN_WINDOW_MS = 20_000L

        /** 알림 직후엔 아직 안 그려졌을 수 있어 잠깐 기다린다. 호출부가 WorkManager 라 블로킹해도 된다. */
        const val DRIVE_MODE_TIMEOUT_MS = 2_500L
        const val DRIVE_MODE_POLL_MS = 200L

        /** UNKNOWN 이면 막지 않는다 — 판별 실패가 정상 전송을 죽이면 안 된다. */
        inline fun pollMode(
            root: () -> AccessibilityNodeInfo?,
            detect: (AccessibilityNodeInfo) -> NaviDriveMode,
        ): NaviDriveMode {
            val deadline = System.currentTimeMillis() + DRIVE_MODE_TIMEOUT_MS
            while (true) {
                root()?.let { node ->
                    val mode = detect(node)
                    if (mode != NaviDriveMode.UNKNOWN) return mode
                }
                if (System.currentTimeMillis() >= deadline) return NaviDriveMode.UNKNOWN
                Thread.sleep(DRIVE_MODE_POLL_MS)
            }
        }
    }
}
