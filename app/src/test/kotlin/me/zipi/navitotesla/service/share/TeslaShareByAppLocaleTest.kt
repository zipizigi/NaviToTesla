package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendPayload
import org.junit.Assert.assertEquals
import java.net.URLEncoder
import java.util.Locale
import org.junit.Test

class TeslaShareByAppLocaleTest {
    private fun payload(
        sendText: String = "서울특별시 중구 세종대로 110",
        displayText: String = "서울특별시 중구 세종대로 110",
        mode: SendMode = SendMode.ROAD,
        viaUrl: Boolean = false,
    ) = SendPayload(sendText, displayText, mode, viaUrl)

    @Test
    fun `english locale wraps raw text as google maps URL`() {
        val raw = "서울특별시 중구 세종대로 110"
        val result = resolveShareIntentText(payload(sendText = raw), Locale.ENGLISH)
        assertEquals(
            "https://maps.google.com/maps?q=" + URLEncoder.encode(raw, "UTF-8"),
            result,
        )
    }

    @Test
    fun `korean locale leaves raw text as-is`() {
        val raw = "서울특별시 중구 세종대로 110"
        val result = resolveShareIntentText(payload(sendText = raw), Locale.KOREAN)
        assertEquals(raw, result)
    }

    @Test
    fun `japanese locale leaves raw text as-is`() {
        val raw = "서울특별시 중구 세종대로 110"
        val result = resolveShareIntentText(payload(sendText = raw), Locale.JAPANESE)
        assertEquals(raw, result)
    }

    @Test
    fun `english locale + already URL-wrapped payload stays untouched`() {
        val alreadyUrl = "https://maps.google.com/maps?q=%EC%84%9C%EC%9A%B8"
        val result = resolveShareIntentText(
            payload(sendText = alreadyUrl, viaUrl = true),
            Locale.ENGLISH,
        )
        assertEquals(alreadyUrl, result)
    }

    @Test
    fun `english locale + GPS payload stays as raw coordinates`() {
        val coords = "37.566645,126.978256"
        val result = resolveShareIntentText(
            payload(sendText = coords, displayText = coords, mode = SendMode.GPS),
            Locale.ENGLISH,
        )
        assertEquals(coords, result)
    }
}
