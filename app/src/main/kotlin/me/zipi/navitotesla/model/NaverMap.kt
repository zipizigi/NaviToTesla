package me.zipi.navitotesla.model

class NaverMap {
    data class Response(
        var items: List<Place>? = null,
    )

    data class Place(
        // title에 HTML 태그 포함될 수 있음 (예: <b>탕화쿵푸</b>마라탕)
        var title: String? = null,
        // 지번주소
        var address: String? = null,
        // 도로명주소
        var roadAddress: String? = null,
        // 경도 × 10^7 (예: "1270904176" → 127.0904176)
        var mapx: String? = null,
        // 위도 × 10^7 (예: "372956780" → 37.2956780)
        var mapy: String? = null,
    ) {
        val name: String
            get() = title?.replace(Regex("<[^>]*>"), "") ?: ""

        val longitude: String
            get() = mapx?.toLongOrNull()?.let { (it / 10_000_000.0).toString() } ?: ""

        val latitude: String
            get() = mapy?.toLongOrNull()?.let { (it / 10_000_000.0).toString() } ?: ""
    }
}
