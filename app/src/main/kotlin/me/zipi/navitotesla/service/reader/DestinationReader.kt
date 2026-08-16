package me.zipi.navitotesla.service.reader

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.zipi.navitotesla.service.poifinder.NaviDriveMode

/** 접근성 노드의 text/contentDescription 을 훑어 목적지를 읽는다. */
interface DestinationReader {
    fun onEvent(
        event: AccessibilityEvent,
        root: () -> AccessibilityNodeInfo?,
    )

    /** 주행 화면이 길안내인지 안전운전인지. */
    fun driveMode(root: () -> AccessibilityNodeInfo?): NaviDriveMode

    fun closeScanWindow()

    companion object {
        const val DEBOUNCE_MS = 200L

        const val SCAN_WINDOW_MS = 20_000L

        const val DRIVE_MODE_TIMEOUT_MS = 2_500L
        const val DRIVE_MODE_POLL_MS = 200L

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
