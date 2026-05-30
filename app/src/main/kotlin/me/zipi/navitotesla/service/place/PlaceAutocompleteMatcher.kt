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
    /**
     * [queryInput] 으로 자동완성을 조회하되, 매칭은 항상 [target](목적지 도로명주소) 기준으로 한다.
     * prefix 조회 시 queryInput(잘린 prefix)≠target 이므로 둘을 분리해야 한다.
     */
    suspend fun query(
        queryInput: String,
        target: String,
    ): AutocompleteResult
}

class RealPlaceAutocompleteMatcher(
    private val client: PlacesAutocompleteClient = PlacesAutocompleteClient,
    private val roadNumberMatcher: RoadNumberMatcher = RoadNumberMatcher(),
) : PlaceAutocompleteMatcher {
    override suspend fun query(
        queryInput: String,
        target: String,
    ): AutocompleteResult {
        val raw = client.fetchPredictions(queryInput)
        val predictions =
            raw.map { PlacePrediction(fullText = it.getFullText(null).toString(), placeId = it.placeId) }
        val matchedIdx = raw.indexOfFirst { roadNumberMatcher.matches(target, listOf(it)) }
        return AutocompleteResult(
            predictions = predictions,
            matched = matchedIdx >= 0,
            matchedPlaceId = predictions.getOrNull(matchedIdx)?.placeId,
        )
    }
}
