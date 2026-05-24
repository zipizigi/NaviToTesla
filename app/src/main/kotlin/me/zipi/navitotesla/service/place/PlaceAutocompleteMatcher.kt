package me.zipi.navitotesla.service.place

interface PlaceAutocompleteMatcher {
    suspend fun isMatch(input: String): Boolean
}

class RealPlaceAutocompleteMatcher(
    private val client: PlacesAutocompleteClient = PlacesAutocompleteClient,
    private val roadNumberMatcher: RoadNumberMatcher = RoadNumberMatcher(),
) : PlaceAutocompleteMatcher {
    override suspend fun isMatch(input: String): Boolean {
        val predictions = client.fetchPredictions(input)
        return roadNumberMatcher.matches(input, predictions)
    }
}
