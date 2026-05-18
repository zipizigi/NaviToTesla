package me.zipi.navitotesla.service.poifinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TMapPoiFinderTest {
    private val finder = TMapPoiFinder()

    @Test
    fun `parseDestination returns last segment for 출발지 to 목적지`() {
        assertEquals("강남역", finder.parseDestination("내 위치 > 강남역"))
    }

    @Test
    fun `parseDestination returns last segment when waypoint exists`() {
        assertEquals("송파구청", finder.parseDestination("내 위치 > 경유지 > 송파구청"))
    }

    @Test
    fun `parseDestination returns last segment for multi waypoints`() {
        assertEquals(
            "도착지",
            finder.parseDestination("출발 > 경유1 > 경유2 > 도착지"),
        )
    }

    @Test
    fun `parseDestination returns trimmed input when no separator present`() {
        assertEquals("단일목적지", finder.parseDestination("  단일목적지  "))
    }

    @Test
    fun `parseDestination skips trailing blank segments`() {
        // "출발 > " → ["출발 ", " "] → last non-blank = "출발"
        assertEquals("출발", finder.parseDestination("출발 > "))
    }

    @Test
    fun `isIgnore returns false for 경로주행 with normal destination text`() {
        assertFalse(finder.isIgnore("경로주행", "내 위치 > 강남역"))
    }

    @Test
    fun `isIgnore returns true when text is 안심주행`() {
        assertTrue(finder.isIgnore("경로주행", "안심주행"))
    }

    @Test
    fun `isIgnore returns true when title is not 경로주행`() {
        assertTrue(finder.isIgnore("일반 알림", "내 위치 > 강남역"))
        assertTrue(finder.isIgnore("", "내 위치 > 강남역"))
    }
}
