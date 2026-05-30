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

        val result = matcher.query("서울특별시 강남구 영동대로 513")

        assertTrue(result.matched)
        assertEquals(1, result.predictions.size)
        assertEquals("대한민국 서울특별시 강남구 영동대로 513", result.predictions[0].fullText)
        assertEquals("pid1", result.predictions[0].placeId)
        assertEquals("pid1", result.matchedPlaceId)
    }
}
