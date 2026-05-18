package me.zipi.navitotesla.service.poifinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoPoiFinderTest {
    private val finder = KakaoPoiFinder()

    @Test
    fun `parseDestination strips 목적지 prefix`() {
        assertEquals("송파구청", finder.parseDestination("목적지 : 송파구청"))
    }

    @Test
    fun `parseDestination trims whitespace`() {
        assertEquals("강남역", finder.parseDestination("  목적지 : 강남역  "))
    }

    @Test
    fun `parseDestination returns input as-is when prefix missing`() {
        assertEquals("그냥문자열", finder.parseDestination("그냥문자열"))
    }

    @Test
    fun `isIgnore returns false for valid 길안내 주행 중 notification`() {
        assertFalse(finder.isIgnore("길안내 주행 중", "목적지 : 송파구청"))
    }

    @Test
    fun `isIgnore returns true when title is not 길안내 주행 중`() {
        assertTrue(finder.isIgnore("일반 알림", "목적지 : 송파구청"))
        assertTrue(finder.isIgnore("", "목적지 : 송파구청"))
    }

    @Test
    fun `isIgnore returns true when text does not contain 목적지 prefix`() {
        assertTrue(finder.isIgnore("길안내 주행 중", "다른 텍스트"))
        assertTrue(finder.isIgnore("길안내 주행 중", ""))
    }
}
