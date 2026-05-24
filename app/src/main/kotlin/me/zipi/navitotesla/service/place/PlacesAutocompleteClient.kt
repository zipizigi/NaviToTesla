package me.zipi.navitotesla.service.place

import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.tasks.await

object PlacesAutocompleteClient {
    @Volatile
    private var client: PlacesClient? = null

    fun setClient(c: PlacesClient) {
        client = c
    }

    /**
     * Places autocomplete 호출. 결과가 빈 리스트면 "자동완성에 없는 주소" 의미 (정상 흐름).
     * 호출 실패(client 미초기화 / 네트워크 / quota / SDK 예외) 는 예외를 그대로 전파해
     * 호출자가 잘못된 영구 cache write 를 피하도록 한다 — 일시 오류가 NotSearchable 로
     * 영구 기록되는 사고 방지.
     */
    @Throws(Exception::class)
    suspend fun fetchPredictions(input: String): List<AutocompletePrediction> {
        val c = client ?: throw IllegalStateException("Places client not initialized")
        val token = AutocompleteSessionToken.newInstance()
        val request =
            FindAutocompletePredictionsRequest
                .builder()
                .setSessionToken(token)
                .setQuery(input)
                .setCountries("KR")
                .setRegionCode("KR")
                .build()
        return c.findAutocompletePredictions(request).await().autocompletePredictions
    }
}
