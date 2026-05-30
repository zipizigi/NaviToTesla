package me.zipi.navitotesla.service.place

import com.google.android.libraries.places.api.model.AutocompletePrediction
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
        val request =
            FindAutocompletePredictionsRequest
                .builder()
                .setQuery(input)
                .setCountries("KR")
                .setRegionCode("KR")
                .setTypesFilter(listOf("street_address"))
                .build()
        return c.findAutocompletePredictions(request).await().autocompletePredictions
    }
}
