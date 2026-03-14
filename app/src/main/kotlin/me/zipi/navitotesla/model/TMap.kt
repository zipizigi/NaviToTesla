package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

class TMap {
    data class SearchPoiResponse(
        val searchPoiInfo: SearchPoiInfo? = null,
    )

    data class SearchPoiInfo(
        val count: Int = 0,
        val page: Int = 0,
        val totalCount: Int = 0,
        val pois: PoiItems,
    )

    data class PoiItems(
        val poi: List<PoiItem> = listOf(),
    )

    data class PoiItem(
        var name: String? = null,
        var upperAddrName: String? = null,
        var middleAddrName: String? = null,
        var lowerAddrName: String? = null,
        var detailAddrName: String? = null,
        var mlClass: String? = null,
        var firstNo: String? = null,
        var secondNo: String? = null,
        var roadName: String? = null,
        var firstBuildNo: String? = null,
        var secondBuildNo: String? = null,
        @SerializedName("noorLat")
        var latitude: String? = null,
        @SerializedName("noorLon")
        var longitude: String? = null,
    ) {
        fun getRoadAddress(withLocalName: Boolean): String {
            if (roadName.isNullOrEmpty() || firstBuildNo.isNullOrEmpty()) return ""
            val sb = StringBuilder()
            sb.append(upperAddrName)
            if (!middleAddrName.isNullOrEmpty()) sb.append(" ").append(middleAddrName)
            sb.append(" ").append(roadName)
            sb.append(" ").append(firstBuildNo)
            if (!secondBuildNo.isNullOrEmpty() && secondBuildNo != "0") sb.append("-").append(secondBuildNo)
            if (!lowerAddrName.isNullOrEmpty() && withLocalName) {
                val lastChar = lowerAddrName!!.last().toString()
                if (lastChar == "동" || lastChar == "로" || lastChar == "가") {
                    sb.append(" (").append(lowerAddrName).append(")")
                }
            }
            return sb.toString()
        }

        val address: String
            get() {
                if (firstNo.isNullOrEmpty()) return ""
                val sb = StringBuilder()
                sb.append(upperAddrName)
                if (!middleAddrName.isNullOrEmpty()) sb.append(" ").append(middleAddrName)
                if (!lowerAddrName.isNullOrEmpty()) sb.append(" ").append(lowerAddrName)
                if (!detailAddrName.isNullOrEmpty()) sb.append(" ").append(detailAddrName)
                if (mlClass == "2") sb.append(" 산")
                sb.append(" ").append(firstNo)
                if (!secondNo.isNullOrEmpty() && secondNo != "0") sb.append("-").append(secondNo)
                return sb.toString()
            }
    }
}
