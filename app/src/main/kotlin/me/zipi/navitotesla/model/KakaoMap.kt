package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName
import java.util.regex.Pattern


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
    ) {
        fun getRoadAddressName(withLocalName: Boolean): String {
            // 시도 구군구 읍동면리 (산) 123(-2)
            var address = roadAddressName ?: ""
            val match = pattern.matcher(addressName)
            if (match.find() && withLocalName) {
                val lowerAddrName = match.group(1)
                if (lowerAddrName != null && lowerAddrName.isNotEmpty()) {
                    val lastChar = lowerAddrName.substring(lowerAddrName.length - 1)
                    if (lastChar == "동" || lastChar == "로" || lastChar == "가") {
                        address += " ($lowerAddrName)"
                    }
                }
            }
            return address
        }

        companion object {
            private val pattern = Pattern.compile("([^\\s]+)\\s(?:산\\s)?\\d+(?:-\\d)?$")
        }
    }


    data class Response<T>(
        var documents: List<T> = listOf()
    )
}