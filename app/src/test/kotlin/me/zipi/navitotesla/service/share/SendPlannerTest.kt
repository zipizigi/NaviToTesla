package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.model.ShareTransport
import me.zipi.navitotesla.service.place.Searchability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.util.Locale

private const val GOOGLE_MAPS_URL_PREFIX = "https://maps.google.com/maps?q="

private fun urlOf(raw: String): String = GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(raw, "UTF-8")

class SendPlannerTest {
    private val poi =
        Poi(
            poiName = "서울특별시청",
            roadAddress = "서울특별시 중구 세종대로 110",
            address = "서울특별시 중구 태평로1가 31",
            latitude = "37.566645",
            longitude = "126.978256",
            packageName = "com.example",
        )

    private val addressOnlyPoi =
        Poi(
            poiName = "서울특별시 중구 세종대로 110",
            roadAddress = "서울특별시 중구 세종대로 110",
            address = "서울특별시 중구 세종대로 110",
            latitude = null,
            longitude = null,
            packageName = "com.example",
        )

    private fun settings(
        default: SendMode,
        fallback: SendMode = default,
        rc: Boolean = false,
        transport: ShareTransport = ShareTransport.API,
        locale: Locale = Locale.KOREAN,
    ) = SendSettings(
        defaultMode = default,
        fallbackMode = fallback,
        treatUnknownAsNotSearchable = rc,
        shareTransport = transport,
        locale = locale,
    )

    // --- 불완전 주소 → 좌표 강등 (Naver 실측: "테슬라 슈퍼차저 여주") ---

    private val vaguePoi =
        Poi(
            poiName = "여주 신세계프리미엄아울렛 수퍼차저",
            roadAddress = "경기도 여주시",
            address = "경기도 여주시 상거동",
            latitude = "37.2402517",
            longitude = "127.6137826",
            packageName = "com.example",
        )

    @Test
    fun `시군구 단위 도로명은 좌표가 있으면 GPS 로 강등`() {
        val payload = SendPlanner.plan(vaguePoi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals(SendMode.GPS, payload.mode)
        assertEquals("37.2402517,127.6137826", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `동 단위 지번도 좌표가 있으면 GPS 로 강등`() {
        val payload = SendPlanner.plan(vaguePoi, Searchability.Searchable, false, settings(SendMode.JIBUN))
        assertEquals(SendMode.GPS, payload.mode)
        assertEquals("37.2402517,127.6137826", payload.sendText)
    }

    @Test
    fun `도로명이 불완전하고 지번이 특정되면 좌표 대신 지번으로 강등`() {
        val jibunPoi = vaguePoi.copy(address = "경기도 여주시 상거동 375")
        val payload = SendPlanner.plan(jibunPoi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals(SendMode.JIBUN, payload.mode)
        assertEquals("경기도 여주시 상거동 375", payload.sendText)
    }

    @Test
    fun `좌표로 강등되면 표시 문구는 POI 이름`() {
        val payload = SendPlanner.plan(vaguePoi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals(SendMode.GPS, payload.mode)
        assertEquals("37.2402517,127.6137826", payload.sendText)
        assertEquals("여주 신세계프리미엄아울렛 수퍼차저", payload.displayText)
    }

    @Test
    fun `불완전 주소라도 좌표가 없으면 강등하지 않음`() {
        val noCoords = vaguePoi.copy(latitude = null, longitude = null)
        val payload = SendPlanner.plan(noCoords, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals(SendMode.ROAD, payload.mode)
    }

    @Test
    fun `NAME 모드는 주소 완전성과 무관하게 유지`() {
        val payload = SendPlanner.plan(vaguePoi, Searchability.Searchable, false, settings(SendMode.NAME))
        assertEquals(SendMode.NAME, payload.mode)
    }

    @Test
    fun `완전한 주소는 강등하지 않음`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals(SendMode.ROAD, payload.mode)
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
    }

    // --- 좌표 형식 (raw GPS payload, classify 결과 무관) ---

    @Test
    fun `coords roadAddress sends raw lat,lng without URL wrap`() {
        val coordsPoi = Poi(poiName = "집", roadAddress = "37.5,127.0", isFavorite = true)
        val payload = SendPlanner.plan(coordsPoi, Searchability.NotSearchable, false, settings(SendMode.NAME))
        assertEquals("37.5,127.0", payload.sendText)
        assertEquals("37.5,127.0", payload.displayText)
        assertEquals(SendMode.GPS, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `coords roadAddress ignores default mode and locale settings`() {
        val coordsPoi = Poi(roadAddress = "37.5, 127.0")
        val payload =
            SendPlanner.plan(
                coordsPoi,
                Searchability.Searchable,
                false,
                settings(SendMode.JIBUN, transport = ShareTransport.APP, locale = Locale.ENGLISH),
            )
        // 좌표는 정책 분기 무관하게 raw 로 — JIBUN 모드, English locale 무시.
        assertEquals("37.5, 127.0", payload.sendText)
        assertEquals(SendMode.GPS, payload.mode)
        assertFalse(payload.viaUrl)
    }

    // --- 즐겨찾기 (isFavorite=true) ---
    // 정책: favorite 은 roadAddress 컬럼이 곧 사용자 의도. NAME 모드만 ROAD 로 강등, 나머지 모드 분기는 일반 flow 와 동일.

    @Test
    fun `favorite + NAME mode demotes to ROAD (favorite uses roadAddress text directly)`() {
        val favPoi = poi.copy(isFavorite = true)
        val payload = SendPlanner.plan(favPoi, Searchability.Searchable, false, settings(SendMode.NAME))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `favorite + not_searchable still wraps roadAddress as URL`() {
        val favPoi = poi.copy(isFavorite = true)
        val payload = SendPlanner.plan(favPoi, Searchability.NotSearchable, false, settings(SendMode.ROAD))
        assertEquals(urlOf("서울특별시 중구 세종대로 110"), payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 일반 PoI SEARCHABLE ---

    @Test
    fun `searchable + default ROAD sends road raw`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `searchable + default JIBUN sends jibun raw`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, false, settings(SendMode.JIBUN))
        assertEquals("서울특별시 중구 태평로1가 31", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `searchable + default NAME wraps as google maps URL with name`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, false, settings(SendMode.NAME))
        assertEquals(urlOf("서울특별시청"), payload.sendText)
        assertEquals("서울특별시청", payload.displayText)
        assertEquals(SendMode.NAME, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 일반 PoI NOT_SEARCHABLE ---

    @Test
    fun `not_searchable + fallback ROAD wraps road as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, false, settings(SendMode.JIBUN, SendMode.ROAD))
        assertEquals(urlOf("서울특별시 중구 세종대로 110"), payload.sendText)
        assertEquals("서울특별시 중구 세종대로 110", payload.displayText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertTrue(payload.viaUrl)
    }

    @Test
    fun `not_searchable + fallback JIBUN wraps jibun as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, false, settings(SendMode.ROAD, SendMode.JIBUN))
        assertEquals(urlOf("서울특별시 중구 태평로1가 31"), payload.sendText)
        assertEquals("서울특별시 중구 태평로1가 31", payload.displayText)
        assertTrue(payload.viaUrl)
    }

    @Test
    fun `not_searchable + fallback NAME wraps name as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, false, settings(SendMode.ROAD, SendMode.NAME))
        assertEquals(urlOf("서울특별시청"), payload.sendText)
        assertEquals("서울특별시청", payload.displayText)
        assertEquals(SendMode.NAME, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- UNKNOWN ---

    @Test
    fun `unknown + RC false uses default mode without URL`() {
        val payload = SendPlanner.plan(poi, Searchability.Unknown, false, settings(SendMode.ROAD, SendMode.JIBUN, rc = false))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `unknown + RC true falls back to fallbackMode with URL`() {
        val payload = SendPlanner.plan(poi, Searchability.Unknown, false, settings(SendMode.ROAD, SendMode.JIBUN, rc = true))
        assertEquals(urlOf("서울특별시 중구 태평로1가 31"), payload.sendText)
        assertEquals("서울특별시 중구 태평로1가 31", payload.displayText)
        assertEquals(SendMode.JIBUN, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 주소 직입력 ---

    @Test
    fun `address-only poi searchable sends raw`() {
        val payload = SendPlanner.plan(addressOnlyPoi, Searchability.Searchable, false, settings(SendMode.ROAD))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `address-only poi not_searchable wraps as URL`() {
        val payload = SendPlanner.plan(addressOnlyPoi, Searchability.NotSearchable, false, settings(SendMode.ROAD))
        assertEquals(urlOf("서울특별시 중구 세종대로 110"), payload.sendText)
        assertEquals("서울특별시 중구 세종대로 110", payload.displayText)
        assertTrue(payload.viaUrl)
    }

    // --- 중복 선택 ---

    @Test
    fun `duplicate-selected demotes NAME to ROAD`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, isDuplicateSelected = true, settings(SendMode.NAME))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `duplicate-selected not_searchable still wraps road as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, isDuplicateSelected = true, settings(SendMode.ROAD))
        assertEquals(urlOf("서울특별시 중구 세종대로 110"), payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 인코딩 계약 anchor (encoder 라이브러리 교체 시 깨지도록 byte-for-byte 고정) ---

    @Test
    fun `URL wrap uses application-x-www-form-urlencoded UTF-8 for spaces and Korean`() {
        // 서울특별시 중구 세종대로 110 → 공백은 +, 한글은 %XX percent-encoded.
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, false, settings(SendMode.JIBUN, SendMode.ROAD))
        assertEquals(
            "https://maps.google.com/maps?q=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C+%EC%A4%91%EA%B5%AC+%EC%84%B8%EC%A2%85%EB%8C%80%EB%A1%9C+110",
            payload.sendText,
        )
    }

    // --- JIBUN fallback when jibun empty ---

    @Test
    fun `jibun mode falls back to road when jibun empty`() {
        val noJibunPoi =
            Poi(
                poiName = "서울특별시청",
                roadAddress = "서울특별시 중구 세종대로 110",
                address = "",
                latitude = "37.566645",
                longitude = "126.978256",
                packageName = "com.example",
            )
        val payload = SendPlanner.plan(noJibunPoi, Searchability.Searchable, false, settings(SendMode.JIBUN))
        assertEquals("서울특별시 중구 세종대로 110", payload.sendText)
        assertEquals(SendMode.JIBUN, payload.mode)
        assertFalse(payload.viaUrl)
    }

    // --- ByApp + non-Korean locale wrap (absorbs TeslaShareByAppLocaleTest matrix) ---

    @Test
    fun `byApp + english locale wraps raw road as URL`() {
        val payload =
            SendPlanner.plan(
                poi,
                Searchability.Searchable,
                false,
                settings(SendMode.ROAD, transport = ShareTransport.APP, locale = Locale.ENGLISH),
            )
        assertEquals(urlOf(poi.getRoadAddress()), payload.sendText)
        assertEquals(poi.getRoadAddress(), payload.displayText)
        assertTrue(payload.viaUrl)
    }

    @Test
    fun `byApp + japanese locale wraps raw road as URL`() {
        val payload =
            SendPlanner.plan(
                poi,
                Searchability.Searchable,
                false,
                settings(SendMode.ROAD, transport = ShareTransport.APP, locale = Locale.JAPANESE),
            )
        assertTrue(payload.viaUrl)
        assertTrue(payload.sendText.startsWith("https://maps.google.com/maps?q="))
    }

    @Test
    fun `byApp + korean locale does NOT wrap raw road`() {
        val payload =
            SendPlanner.plan(
                poi,
                Searchability.Searchable,
                false,
                settings(SendMode.ROAD, transport = ShareTransport.APP, locale = Locale.KOREAN),
            )
        assertEquals(poi.getRoadAddress(), payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `byApi + english locale does NOT wrap (server gets ko-KR via ShareRequest)`() {
        val payload =
            SendPlanner.plan(
                poi,
                Searchability.Searchable,
                false,
                settings(SendMode.ROAD, transport = ShareTransport.API, locale = Locale.ENGLISH),
            )
        assertEquals(poi.getRoadAddress(), payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `byApp + english locale + already URL-wrapped NAME mode stays single-wrapped`() {
        // NAME mode produces URL via SendPlanner's existing logic; the byAppNonKorean condition
        // should not double-wrap (sendText still starts with one https:// and contains one ?q=).
        val payload =
            SendPlanner.plan(
                poi,
                Searchability.Searchable,
                false,
                settings(SendMode.NAME, transport = ShareTransport.APP, locale = Locale.ENGLISH),
            )
        assertEquals(urlOf(poi.poiName!!), payload.sendText)
        assertTrue(payload.viaUrl)
    }
}
