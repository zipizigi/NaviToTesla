package me.zipi.navitotesla.model

class NaverFusionSearch {
    data class Response(
        var totalCount: Int = 0,
        var items: List<Place>? = null,
    )

    data class Place(
        var name: String? = null,
        // 지번주소
        var address: String? = null,
        // 도로명주소
        var roadAddress: String? = null,
        var latitude: Double? = null,
        var longitude: Double? = null,
    )
}
