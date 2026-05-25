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
