package me.zipi.navitotesla.service.place

/** SDK 비의존 도메인 예측. fullText 는 캐시키 정규화 전 원문. */
data class PlacePrediction(
    val fullText: String,
    val placeId: String?,
)

data class AutocompleteResult(
    val predictions: List<PlacePrediction>,
    val matched: Boolean,
    val matchedPlaceId: String?,
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
        val predictions =
            raw.map { PlacePrediction(fullText = it.getFullText(null).toString(), placeId = it.placeId) }
        val matchedIdx = raw.indexOfFirst { roadNumberMatcher.matches(input, listOf(it)) }
        return AutocompleteResult(
            predictions = predictions,
            matched = matchedIdx >= 0,
            matchedPlaceId = matchedIdx.takeIf { it >= 0 }?.let { predictions[it].placeId },
        )
    }
}
