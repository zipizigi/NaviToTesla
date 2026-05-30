package me.zipi.navitotesla.service.place

/** Places Autocomplete 응답 최대 건수(API 페이지 상한). size==이 값이면 절단됐을 수 있다. */
const val MAX_PREDICTIONS = 5

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
            matchedPlaceId = predictions.getOrNull(matchedIdx)?.placeId,
        )
    }
}
