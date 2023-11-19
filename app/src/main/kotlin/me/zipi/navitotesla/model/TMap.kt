package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

class TMap {
    data class SearchPoiResponse(
        val searchPoiInfo: SearchPoiInfo? = null
    )


    data class SearchPoiInfo(
        val count: Int = 0,
        val page: Int = 0,
        val totalCount: Int = 0,
        val pois: PoiItems,
    )


    data class PoiItems(
        val poi: List<PoiItem> = listOf()
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
            if (roadName!!.isNotEmpty() && firstBuildNo!!.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append(upperAddrName)
                if (middleAddrName!!.isNotEmpty()) {
                    sb.append(" ").append(middleAddrName)
                }
                if (roadName!!.isNotEmpty()) {
                    sb.append(" ").append(roadName)
                }
                if (firstBuildNo!!.isNotEmpty()) {
                    sb.append(" ").append(firstBuildNo)
                }
                if (secondBuildNo!!.isNotEmpty() && secondBuildNo != "0") {
                    sb.append("-").append(secondBuildNo)
                }

                // 법정동(동/로/가)가 있을 경우 추가항목으로 (법정동)을 붙여준다.
                // 건물명이 있을 경우 (법정동, 건물명) 표시도 가능하다.
                if (lowerAddrName!!.isNotEmpty() && withLocalName) {
                    val lastChar = lowerAddrName!!.substring(lowerAddrName!!.length - 1)
                    if (lastChar == "동" || lastChar == "로" || lastChar == "가") {
                        sb.append(" (").append(lowerAddrName).append(")")
                    }
                }
                return sb.toString()
            }
            return ""
        }

        val address: String
            get() {
                if (firstNo == null || firstNo!!.isEmpty()) {
                    return ""
                }
                val sb = StringBuilder()
                sb.append(upperAddrName)
                if (middleAddrName!!.isNotEmpty()) {
                    sb.append(" ").append(middleAddrName)
                }
                if (lowerAddrName!!.isNotEmpty()) {
                    sb.append(" ").append(lowerAddrName)
                }
                if (detailAddrName!!.isNotEmpty()) {
                    sb.append(" ").append(detailAddrName)
                }
                if (mlClass == "2") {
                    sb.append(" 산")
                }
                sb.append(" ").append(firstNo)
                if (secondNo!!.isNotEmpty() && secondNo != "0") {
                    sb.append("-").append(secondNo)
                }
                return sb.toString()
            }
    }
}