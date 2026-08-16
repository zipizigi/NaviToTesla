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

    @Test
    fun `isKakaoNavi distinguishes Kakao vs others`() {
        assertTrue(PoiFinderFactory.isKakaoNavi("com.locnall.KimGiSa"))
        assertFalse(PoiFinderFactory.isKakaoNavi("com.nhn.android.nmap"))
    }

    @Test
    fun `isAccessibilityRequired is always true for Naver`() {
        assertTrue(PoiFinderFactory.isAccessibilityRequired("com.nhn.android.nmap", "무엇이든"))
    }

    @Test
    fun `isAccessibilityRequired is false for legacy Kakao notification`() {
        assertFalse(PoiFinderFactory.isAccessibilityRequired("com.locnall.KimGiSa", "목적지 : 송파구청"))
    }

    @Test
    fun `isAccessibilityRequired is true for new Kakao notification`() {
        assertTrue(
            PoiFinderFactory.isAccessibilityRequired("com.locnall.KimGiSa", "빠르고 즐거운 운전, 카카오내비"),
        )
    }

    @Test
    fun `isAccessibilityRequired is false for TMap`() {
        assertFalse(PoiFinderFactory.isAccessibilityRequired("com.skt.tmap.ku", "어디까지"))
    }

    @Test
    fun `clearAllCapturedDestinations empties every finder cache`() {
        KakaoPoiFinder.addDestination("정자역")
        NaverPoiFinder.addDestination("판교역")
        PoiFinderFactory.clearAllCapturedDestinations()
        assertTrue(KakaoPoiFinder.isDestinationEmpty())
        assertTrue(NaverPoiFinder.isDestinationEmpty())
    }
}
