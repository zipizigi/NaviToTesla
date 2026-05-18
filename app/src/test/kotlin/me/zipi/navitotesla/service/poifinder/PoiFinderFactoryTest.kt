package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.NotSupportedNaviException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiFinderFactoryTest {
    @Test
    fun `isNaviSupport returns true for TMap (KU)`() {
        assertTrue(PoiFinderFactory.isNaviSupport("com.skt.tmap.ku"))
    }

    @Test
    fun `isNaviSupport returns true for TMap (SK)`() {
        assertTrue(PoiFinderFactory.isNaviSupport("com.skt.skaf.l001mtm091"))
    }

    @Test
    fun `isNaviSupport returns true for KakaoNavi`() {
        assertTrue(PoiFinderFactory.isNaviSupport("com.locnall.KimGiSa"))
    }

    @Test
    fun `isNaviSupport returns true for Naver Map`() {
        assertTrue(PoiFinderFactory.isNaviSupport("com.nhn.android.nmap"))
    }

    @Test
    fun `isNaviSupport is case insensitive`() {
        assertTrue(PoiFinderFactory.isNaviSupport("COM.SKT.TMAP.KU"))
        assertTrue(PoiFinderFactory.isNaviSupport("com.LOCNALL.kimgisa"))
    }

    @Test
    fun `isNaviSupport returns false for unsupported package`() {
        assertFalse(PoiFinderFactory.isNaviSupport("com.google.android.apps.maps"))
        assertFalse(PoiFinderFactory.isNaviSupport("com.example.unknown"))
        assertFalse(PoiFinderFactory.isNaviSupport(""))
    }

    @Test
    fun `isNaverMap distinguishes Naver vs others`() {
        assertTrue(PoiFinderFactory.isNaverMap("com.nhn.android.nmap"))
        assertFalse(PoiFinderFactory.isNaverMap("com.skt.tmap.ku"))
        assertFalse(PoiFinderFactory.isNaverMap("com.locnall.KimGiSa"))
    }

    @Test
    fun `getPoiFinder returns TMapPoiFinder for TMap packages`() {
        assertTrue(PoiFinderFactory.getPoiFinder("com.skt.tmap.ku") is TMapPoiFinder)
        assertTrue(PoiFinderFactory.getPoiFinder("com.skt.skaf.l001mtm091") is TMapPoiFinder)
    }

    @Test
    fun `getPoiFinder returns KakaoPoiFinder for KakaoNavi`() {
        assertTrue(PoiFinderFactory.getPoiFinder("com.locnall.KimGiSa") is KakaoPoiFinder)
    }

    @Test
    fun `getPoiFinder returns NaverPoiFinder for Naver Map`() {
        assertTrue(PoiFinderFactory.getPoiFinder("com.nhn.android.nmap") is NaverPoiFinder)
    }

    @Test
    fun `getPoiFinder throws NotSupportedNaviException for unknown package`() {
        assertThrows(NotSupportedNaviException::class.java) {
            PoiFinderFactory.getPoiFinder("com.example.unknown")
        }
    }
}
