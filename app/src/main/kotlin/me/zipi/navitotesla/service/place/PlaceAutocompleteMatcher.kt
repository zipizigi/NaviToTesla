package me.zipi.navitotesla.service.place

/** SDK 비의존 도메인 예측. fullText 는 캐시키 정규화 전 원문. */
data class PlacePrediction(
    val fullText: String,
    val placeId: String?,
)

data class AutocompleteResult(
    val predictions: List<PlacePrediction>,
    val matched: Boolean,
)

interface PlaceAutocompleteMatcher {
    suspend fun query(input: String): AutocompleteResult
}

class RealPlaceAutocompleteMatcher(
    private val client: PlacesAutocompleteClient = PlacesAutocompleteClient,
    private val roadNumberMatcher: RoadNumberMatcher = RoadNumberMatcher(),
) : PlaceAutocompleteMatcher {
    override suspend fun query(input: String): AutocompleteResult {
        val raw = client.fetchPredictions(input)
        val matched = roadNumberMatcher.matches(input, raw)
        val predictions =
            raw.map { PlacePrediction(fullText = it.getFullText(null).toString(), placeId = it.placeId) }
        return AutocompleteResult(predictions = predictions, matched = matched)
    }
}
