package me.zipi.navitotesla.service.place

import android.text.SpannableString
import com.google.android.libraries.places.api.model.AutocompletePrediction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealPlaceAutocompleteMatcherTest {
    private fun prediction(fullText: String, placeId: String): AutocompletePrediction {
        val spannable = mockk<SpannableString>()
        every { spannable.toString() } returns fullText
        val p = mockk<AutocompletePrediction>()
        every { p.getFullText(null) } returns spannable
        every { p.placeId } returns placeId
        return p
    }

    @Test
    fun `query maps predictions and computes match`() = runBlocking {
        val client = mockk<PlacesAutocompleteClient>()
        coEvery { client.fetchPredictions("서울특별시 강남구 영동대로 513") } returns
            listOf(prediction("대한민국 서울특별시 강남구 영동대로 513", "pid1"))
        val matcher = RealPlaceAutocompleteMatcher(client = client)

        val result = matcher.query("서울특별시 강남구 영동대로 513", "서울특별시 강남구 영동대로 513")

        assertTrue(result.matched)
        assertEquals(1, result.predictions.size)
        assertEquals("대한민국 서울특별시 강남구 영동대로 513", result.predictions[0].fullText)
        assertEquals("pid1", result.predictions[0].placeId)
        assertEquals("pid1", result.matchedPlaceId)
    }

    @Test
    fun `match is against target not queryInput`() = runBlocking {
        // prefix '사직로 16' 으로 조회하지만 매칭은 목적지 '사직로 161' 기준이어야 한다.
        // 잘못 구현하면 prefix '16' 이 '사직로 16'(다른 건물)에 매칭돼 그 placeId 를 돌려준다.
        val client = mockk<PlacesAutocompleteClient>()
        coEvery { client.fetchPredictions("서울 종로구 사직로 16") } returns
            listOf(
                prediction("서울 종로구 사직로 161", "pid-161"),
                prediction("서울 종로구 사직로 16", "pid-16"),
            )
        val matcher = RealPlaceAutocompleteMatcher(client = client)

        val result = matcher.query("서울 종로구 사직로 16", "서울 종로구 사직로 161")

        assertTrue(result.matched)
        assertEquals("pid-161", result.matchedPlaceId)
    }

    @Test
    fun `not matched when target absent even if queryInput matches a different prediction`() = runBlocking {
        // prefix '사직로 16' 은 '사직로 16' 예측과 일치하지만, 목적지 '사직로 169' 는 예측에 없다 → 미매칭.
        val client = mockk<PlacesAutocompleteClient>()
        coEvery { client.fetchPredictions("서울 종로구 사직로 16") } returns
            listOf(prediction("서울 종로구 사직로 16", "pid-16"))
        val matcher = RealPlaceAutocompleteMatcher(client = client)

        val result = matcher.query("서울 종로구 사직로 16", "서울 종로구 사직로 169")

        assertEquals(false, result.matched)
        assertEquals(null, result.matchedPlaceId)
    }
}
