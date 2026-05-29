package me.zipi.navitotesla.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiTest {
    @Test
    fun `isCoordsAddress matches typical lat,lng`() {
        assertTrue(Poi(roadAddress = "37.5,127.0").isCoordsAddress())
        assertTrue(Poi(roadAddress = "37.566645,126.978256").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress matches with space after comma`() {
        assertTrue(Poi(roadAddress = "37.5, 127.0").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress matches negative coords`() {
        assertTrue(Poi(roadAddress = "-37.5,-127.0").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress matches integer coords`() {
        assertTrue(Poi(roadAddress = "37,127").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress tolerates leading or trailing whitespace`() {
        assertTrue(Poi(roadAddress = " 37.5,127.0 ").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress rejects null literal from getGpsAddress fallback`() {
        // Poi.getGpsAddress() 가 null lat/lng 일 때 만들어내는 "null,null" 은 좌표 아님.
        assertFalse(Poi(roadAddress = "null,null").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress rejects normal address`() {
        assertFalse(Poi(roadAddress = "서울특별시 중구 세종대로 110").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress rejects empty or null roadAddress`() {
        assertFalse(Poi(roadAddress = null).isCoordsAddress())
        assertFalse(Poi(roadAddress = "").isCoordsAddress())
    }

    @Test
    fun `isCoordsAddress rejects partial coords`() {
        assertFalse(Poi(roadAddress = "37.5").isCoordsAddress())
        assertFalse(Poi(roadAddress = "37.5,").isCoordsAddress())
        assertFalse(Poi(roadAddress = ",127.0").isCoordsAddress())
    }
}
