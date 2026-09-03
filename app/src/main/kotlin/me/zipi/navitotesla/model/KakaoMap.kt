package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

class KakaoMap {
    data class Place(
        val id: String? = null,
        @SerializedName("place_name")
        val placeName: String? = null,
        @SerializedName("address_name")
        val addressName: String? = null,
        @SerializedName("road_address_name")
        val roadAddressName: String? = null,
        @SerializedName("x")
        val longitude: String? = null,
        @SerializedName("y")
        val latitude: String? = null,
    )

    data class Response<T>(
        var documents: List<T> = listOf(),
    )
}
