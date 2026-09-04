package me.zipi.navitotesla.service.poifinder

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KakaoPoiFinderTest {
    private val finder = KakaoPoiFinder()

    @Before
    @After
    fun clear() {
        KakaoPoiFinder.clearDestination()
        KakaoPoiFinder.setDriveModeProvider(null)
    }

    // ---- 4.48 이하: 알림 본문에 목적지가 있다

    @Test
    fun `parseDestination strips 목적지 prefix`() {
        assertEquals("송파구청", finder.parseDestination("목적지 : 송파구청"))
    }

    @Test
    fun `parseDestination trims whitespace`() {
        assertEquals("강남역", finder.parseDestination("  목적지 : 강남역  "))
    }

    @Test
    fun `isIgnore returns false for legacy notification`() {
        assertFalse(finder.isIgnore("길안내 주행 중", "목적지 : 송파구청"))
    }

    @Test
    fun `isIgnore returns false for legacy notification with insurance title`() {
        assertFalse(finder.isIgnore("보험을 켜고 길안내 주행 중", "목적지 : 송파구청"))
    }

    @Test
    fun `isIgnore returns true when title is not guidance`() {
        assertTrue(finder.isIgnore("일반 알림", "목적지 : 송파구청"))
        assertTrue(finder.isIgnore("", "목적지 : 송파구청"))
    }

    // ---- 4.49 이상: 접근성으로 저장해 둔 목적지를 쓴다

    @Test
    fun `parseDestination falls back to captured destination`() {
        KakaoPoiFinder.addDestination("정자역")
        assertEquals("정자역", finder.parseDestination("빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `parseDestination returns empty when nothing captured`() {
        assertEquals("", finder.parseDestination("빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `isIgnore returns false when captured destination exists`() {
        KakaoPoiFinder.addDestination("정자역")
        assertFalse(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `isIgnore returns true when nothing captured`() {
        assertTrue(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `legacy text wins over captured destination`() {
        KakaoPoiFinder.addDestination("정자역")
        assertEquals("송파구청", finder.parseDestination("목적지 : 송파구청"))
    }

    // ---- 캡처 값 관리

    @Test
    fun `addDestination normalizes non breaking characters`() {
        val raw = "\uACBD\uAE30\u00A0\uC131\uB0A8\uC2DC\u00A0\uBD84\uB2F9\uAD6C\u00A0\uC131\uB0A8\uB300\uB85C\u00A040\u20111"
        KakaoPoiFinder.addDestination(raw)
        assertEquals("\uACBD\uAE30 \uC131\uB0A8\uC2DC \uBD84\uB2F9\uAD6C \uC131\uB0A8\uB300\uB85C 40-1", finder.parseDestination(""))
    }

    @Test
    fun `addDestination ignores blank`() {
        KakaoPoiFinder.addDestination("   ")
        assertTrue(KakaoPoiFinder.isDestinationEmpty())
    }

    @Test
    fun `consumeCapturedDestination drops the value`() {
        KakaoPoiFinder.addDestination("정자역")
        finder.consumeCapturedDestination()
        assertTrue(KakaoPoiFinder.isDestinationEmpty())
        assertTrue(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    // ---- 안전운전(안심주행) 은 제목이 다르다. 카카오 리소스 car_noti_safety_drive_title 기준.

    @Test
    fun `isIgnore returns true for safe drive notification`() {
        KakaoPoiFinder.addDestination("정자역")
        assertTrue(finder.isIgnore("안전운전 주행 중", "빠르고 즐거운 운전, 카카오내비"))
        assertTrue(finder.isIgnore("보험을 켜고 안전운전 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `isIgnore returns true for resume guidance notification`() {
        KakaoPoiFinder.addDestination("정자역")
        assertTrue(finder.isIgnore("오리역까지", "이어서 주행하시겠습니까?"))
    }

    @Test
    fun `guidance and safe drive share the same body text`() {
        KakaoPoiFinder.addDestination("정자역")
        val body = "빠르고 즐거운 운전, 카카오내비"
        assertFalse(finder.isIgnore("길안내 주행 중", body))
        assertTrue(finder.isIgnore("안전운전 주행 중", body))
    }

    // ---- 화면 모드 판별. 안전운전 진입에도 길안내 제목이 뜨므로 이게 실질 방어선이다.

    @Test
    fun `isIgnore returns true when screen shows safe drive`() {
        KakaoPoiFinder.addDestination("정자역")
        KakaoPoiFinder.setDriveModeProvider { NaviDriveMode.SAFE_DRIVE }
        assertTrue(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `isIgnore returns false when screen shows guidance`() {
        KakaoPoiFinder.addDestination("정자역")
        KakaoPoiFinder.setDriveModeProvider { NaviDriveMode.GUIDANCE }
        assertFalse(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `isIgnore allows send when mode is unknown`() {
        KakaoPoiFinder.addDestination("정자역")
        KakaoPoiFinder.setDriveModeProvider { NaviDriveMode.UNKNOWN }
        assertFalse(finder.isIgnore("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `legacy notification is not affected by screen mode`() {
        KakaoPoiFinder.setDriveModeProvider { NaviDriveMode.SAFE_DRIVE }
        assertFalse(finder.isIgnore("길안내 주행 중", "목적지 : 송파구청"))
    }

    @Test
    fun `hasLegacyDestination detects old notification format`() {
        assertTrue(KakaoPoiFinder.hasLegacyDestination("목적지 : 송파구청"))
        assertFalse(KakaoPoiFinder.hasLegacyDestination("빠르고 즐거운 운전, 카카오내비"))
    }

    // === 무시 사유 (무성 실패 원인 구분) ===

    @Test
    fun `ignoreReason 안전운전 제목은 SAFE_TITLE`() {
        KakaoPoiFinder.addDestination("정자역")
        assertEquals(IgnoreReason.SAFE_TITLE, finder.ignoreReason("안전운전 주행 중", "빠르고 즐거운 운전, 카카오내비"))
        assertEquals(
            IgnoreReason.SAFE_TITLE,
            finder.ignoreReason("보험을 켜고 안전운전 주행 중", "빠르고 즐거운 운전, 카카오내비"),
        )
    }

    @Test
    fun `ignoreReason 화이트리스트 밖 제목은 TITLE_MISMATCH`() {
        KakaoPoiFinder.addDestination("정자역")
        assertEquals(IgnoreReason.TITLE_MISMATCH, finder.ignoreReason("길안내 종료 중", "빠르고 즐거운 운전, 카카오내비"))
        assertEquals(IgnoreReason.TITLE_MISMATCH, finder.ignoreReason("", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `ignoreReason 캡처 없으면 NO_CAPTURE`() {
        assertEquals(IgnoreReason.NO_CAPTURE, finder.ignoreReason("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
    }

    @Test
    fun `ignoreReason 정상 경로는 null`() {
        KakaoPoiFinder.addDestination("정자역")
        assertEquals(null, finder.ignoreReason("길안내 주행 중", "빠르고 즐거운 운전, 카카오내비"))
        assertEquals(null, finder.ignoreReason("길안내 주행 중", "목적지 : 송파구청"))
    }
}
