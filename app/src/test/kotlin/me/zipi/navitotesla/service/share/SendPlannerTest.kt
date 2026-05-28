package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.service.place.Searchability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

private const val GOOGLE_MAPS_URL_PREFIX = "https://maps.google.com/maps?q="

private fun urlOf(raw: String): String = GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(raw, "UTF-8")

class SendPlannerTest {
    private val poi =
        Poi(
            poiName = "헬로방방 기흥구청점",
            roadAddress = "경기도 용인시 기흥구 구갈로 55",
            address = "경기도 용인시 기흥구 구갈동 123-4",
            latitude = "37.279398",
            longitude = "127.110960",
            packageName = "com.example",
        )

    private val addressOnlyPoi =
        Poi(
            poiName = "경기도 용인시 기흥구 구갈로 55",
            roadAddress = "경기도 용인시 기흥구 구갈로 55",
            address = "경기도 용인시 기흥구 구갈로 55",
            latitude = null,
            longitude = null,
            packageName = "com.example",
        )

    private fun settings(
        default: SendMode,
        fallback: SendMode = default,
        rc: Boolean = false,
    ) = SendSettings(defaultMode = default, fallbackMode = fallback, treatUnknownAsNotSearchable = rc)

    // --- 즐겨찾기 explicit (registered 우선) ---

    @Test
    fun `registered ROAD ignores settings and uses road raw`() {
        val payload =
            SendPlanner.plan(
                poi = poi,
                searchability = Searchability.NotSearchable, // 무시되어야
                registeredSentMode = PoiAddressEntity.SENT_MODE_ROAD,
                isDuplicateSelected = false,
                settings = settings(SendMode.JIBUN), // 무시되어야
            )
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.displayText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `registered JIBUN uses jibun raw`() {
        val payload =
            SendPlanner.plan(
                poi = poi,
                searchability = Searchability.NotSearchable,
                registeredSentMode = PoiAddressEntity.SENT_MODE_JIBUN,
                isDuplicateSelected = false,
                settings = settings(SendMode.ROAD),
            )
        assertEquals("경기도 용인시 기흥구 구갈동 123-4", payload.sendText)
        assertEquals(SendMode.JIBUN, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `registered GPS uses lat,lng raw`() {
        val payload =
            SendPlanner.plan(
                poi = poi,
                searchability = Searchability.Searchable,
                registeredSentMode = PoiAddressEntity.SENT_MODE_GPS,
                isDuplicateSelected = false,
                settings = settings(SendMode.ROAD),
            )
        assertEquals("37.279398,127.110960", payload.sendText)
        assertEquals("37.279398,127.110960", payload.displayText)
        assertEquals(SendMode.GPS, payload.mode)
        assertFalse(payload.viaUrl)
    }

    // --- 일반 PoI SEARCHABLE ---

    @Test
    fun `searchable + default ROAD sends road raw`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, null, false, settings(SendMode.ROAD))
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `searchable + default JIBUN sends jibun raw`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, null, false, settings(SendMode.JIBUN))
        assertEquals("경기도 용인시 기흥구 구갈동 123-4", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `searchable + default NAME wraps as google maps URL with name`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, null, false, settings(SendMode.NAME))
        assertEquals(urlOf("헬로방방 기흥구청점"), payload.sendText)
        assertEquals("헬로방방 기흥구청점", payload.displayText)
        assertEquals(SendMode.NAME, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 일반 PoI NOT_SEARCHABLE ---

    @Test
    fun `not_searchable + fallback ROAD wraps road as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, null, false, settings(SendMode.JIBUN, SendMode.ROAD))
        assertEquals(urlOf("경기도 용인시 기흥구 구갈로 55"), payload.sendText)
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.displayText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertTrue(payload.viaUrl)
    }

    @Test
    fun `not_searchable + fallback JIBUN wraps jibun as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, null, false, settings(SendMode.ROAD, SendMode.JIBUN))
        assertEquals(urlOf("경기도 용인시 기흥구 구갈동 123-4"), payload.sendText)
        assertEquals("경기도 용인시 기흥구 구갈동 123-4", payload.displayText)
        assertTrue(payload.viaUrl)
    }

    @Test
    fun `not_searchable + fallback NAME wraps name as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, null, false, settings(SendMode.ROAD, SendMode.NAME))
        assertEquals(urlOf("헬로방방 기흥구청점"), payload.sendText)
        assertEquals("헬로방방 기흥구청점", payload.displayText)
        assertEquals(SendMode.NAME, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- UNKNOWN ---

    @Test
    fun `unknown + RC false uses default mode without URL`() {
        val payload = SendPlanner.plan(poi, Searchability.Unknown, null, false, settings(SendMode.ROAD, SendMode.JIBUN, rc = false))
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `unknown + RC true falls back to fallbackMode with URL`() {
        val payload = SendPlanner.plan(poi, Searchability.Unknown, null, false, settings(SendMode.ROAD, SendMode.JIBUN, rc = true))
        assertEquals(urlOf("경기도 용인시 기흥구 구갈동 123-4"), payload.sendText)
        assertEquals("경기도 용인시 기흥구 구갈동 123-4", payload.displayText)
        assertEquals(SendMode.JIBUN, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 주소 직입력 ---

    @Test
    fun `address-only poi searchable sends raw`() {
        val payload = SendPlanner.plan(addressOnlyPoi, Searchability.Searchable, null, false, settings(SendMode.ROAD))
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `address-only poi not_searchable wraps as URL`() {
        val payload = SendPlanner.plan(addressOnlyPoi, Searchability.NotSearchable, null, false, settings(SendMode.ROAD))
        assertEquals(urlOf("경기도 용인시 기흥구 구갈로 55"), payload.sendText)
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.displayText)
        assertTrue(payload.viaUrl)
    }

    // --- 중복 선택 ---

    @Test
    fun `duplicate-selected demotes NAME to ROAD`() {
        val payload = SendPlanner.plan(poi, Searchability.Searchable, null, isDuplicateSelected = true, settings(SendMode.NAME))
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertFalse(payload.viaUrl)
    }

    @Test
    fun `duplicate-selected not_searchable still wraps road as URL`() {
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, null, isDuplicateSelected = true, settings(SendMode.ROAD))
        assertEquals(urlOf("경기도 용인시 기흥구 구갈로 55"), payload.sendText)
        assertEquals(SendMode.ROAD, payload.mode)
        assertTrue(payload.viaUrl)
    }

    // --- 인코딩 계약 anchor (encoder 라이브러리 교체 시 깨지도록 byte-for-byte 고정) ---

    @Test
    fun `URL wrap uses application-x-www-form-urlencoded UTF-8 for spaces and Korean`() {
        // 헬로방방 기흥구청점 → 공백은 +, 한글은 %XX percent-encoded.
        val payload = SendPlanner.plan(poi, Searchability.NotSearchable, null, false, settings(SendMode.ROAD, SendMode.NAME))
        assertEquals(
            "https://maps.google.com/maps?q=%ED%97%AC%EB%A1%9C%EB%B0%A9%EB%B0%A9+%EA%B8%B0%ED%9D%A5%EA%B5%AC%EC%B2%AD%EC%A0%90",
            payload.sendText,
        )
    }

    // --- JIBUN fallback when jibun empty ---

    @Test
    fun `jibun mode falls back to road when jibun empty`() {
        val noJibunPoi =
            Poi(
                poiName = "헬로방방 기흥구청점",
                roadAddress = "경기도 용인시 기흥구 구갈로 55",
                address = "",
                latitude = "37.279398",
                longitude = "127.110960",
                packageName = "com.example",
            )
        val payload = SendPlanner.plan(noJibunPoi, Searchability.Searchable, null, false, settings(SendMode.JIBUN))
        assertEquals("경기도 용인시 기흥구 구갈로 55", payload.sendText)
        assertEquals(SendMode.JIBUN, payload.mode)
        assertFalse(payload.viaUrl)
    }
}
