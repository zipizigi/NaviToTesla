package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

class NaverMap {
    data class Response(
        var result: Result? = null,
    )

    data class Result(
        var site: Site? = null,
    )

    data class Site(
        var list: List<Place>? = null,
    )

    data class Place(
        var name: String? = null,
        // 지번주소
        var address: String? = null,
        // 도로명주소
        var roadAddress: String? = null,
        // 지번만. 봉래동2가 122-21
        var abbrAddress: String? = null,
        @SerializedName("x")
        var longitude: String? = null,
        @SerializedName("y")
        var latitude: String? = null,
    ) {
        fun getRoadAddressName(withLocalName: Boolean): String {
            val addr = roadAddress
            val abbr = abbrAddress
            return if (withLocalName && addr != null && abbr != null) {
                "$addr (${abbr.split(" ").firstOrNull().orEmpty()})"
            } else {
                addr ?: ""
            }
        }
    }
}
