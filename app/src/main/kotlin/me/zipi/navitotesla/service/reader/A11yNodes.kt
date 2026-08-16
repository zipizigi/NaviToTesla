package me.zipi.navitotesla.service.reader

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** 접근성 트리 순회 공통 유틸. 무한 트리를 만나도 멈추도록 상한을 둔다. */
internal object A11yNodes {
    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 3000

    /** 조건을 만족하는 첫 노드의 값을 찾는다. */
    fun <T> findFirst(
        root: AccessibilityNodeInfo?,
        select: (AccessibilityNodeInfo) -> T?,
    ): T? = findFirst(root, select, intArrayOf(0), 0)

    private fun <T> findFirst(
        node: AccessibilityNodeInfo?,
        select: (AccessibilityNodeInfo) -> T?,
        visited: IntArray,
        depth: Int,
    ): T? {
        if (node == null || depth > MAX_DEPTH || visited[0] > MAX_NODES) return null
        visited[0]++
        select(node)?.let { return it }
        for (i in 0 until node.childCount) {
            findFirst(node.getChild(i), select, visited, depth + 1)?.let { return it }
        }
        return null
    }

    /** 보이는 노드의 text 와 화면 좌표를 모은다. */
    fun visibleTexts(node: AccessibilityNodeInfo?): List<Pair<String, Rect>> {
        val out = mutableListOf<Pair<String, Rect>>()
        collectTexts(node, out, intArrayOf(0), 0)
        return out
    }

    private fun collectTexts(
        node: AccessibilityNodeInfo?,
        out: MutableList<Pair<String, Rect>>,
        visited: IntArray,
        depth: Int,
    ) {
        if (node == null || depth > MAX_DEPTH || visited[0] > MAX_NODES) return
        if (!node.isVisibleToUser) return
        visited[0]++
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            out.add(it to rect)
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, visited, depth + 1)
        }
    }

    /** text 와 contentDescription 을 모두 모은다. 마커 탐색용. */
    fun labels(node: AccessibilityNodeInfo?): Set<String> {
        val out = mutableSetOf<String>()
        collectLabels(node, out, intArrayOf(0), 0)
        return out
    }

    private fun collectLabels(
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
            collectLabels(node.getChild(i), out, visited, depth + 1)
        }
    }

    fun hasViewId(
        root: AccessibilityNodeInfo,
        viewId: String,
    ): Boolean = !root.findAccessibilityNodeInfosByViewId(viewId).isNullOrEmpty()
}
