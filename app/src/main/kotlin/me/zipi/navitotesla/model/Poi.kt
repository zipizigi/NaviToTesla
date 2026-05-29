package me.zipi.navitotesla.model

data class Poi(
    val poiName: String? = null,
    private val roadAddress: String? = null,
    private val address: String? = null,
    val longitude: String? = null,
    val latitude: String? = null,
    val packageName: String = "",
    val isDuplicate: Boolean = false,
    /** registered favorite 여부. true 면 roadAddress 컬럼 값 그대로 share (모드 전환 없음). */
    val isFavorite: Boolean = false,
) {
    @Suppress("unused")
    fun isAddressEmpty(): Boolean = roadAddress.isNullOrEmpty() && address.isNullOrEmpty()

    @Suppress("unused")
    fun isGpsEmpty(): Boolean = (longitude == null || latitude == null)

    /**
     * roadAddress 가 "lat,lng" 좌표 문자열인지 검사.
     * favorite 의 GPS 모드 마이그레이션 결과 또는 사용자가 직접 좌표 입력한 경우 매칭.
     * "null,null" (Poi.getGpsAddress 의 null lat/lng fallback) 같은 비숫자 문자열은 제외.
     */
    fun isCoordsAddress(): Boolean {
        val road = roadAddress?.trim() ?: return false
        return COORDS_PATTERN.matches(road)
    }

    companion object {
        private val COORDS_PATTERN = Regex("^-?\\d+(?:\\.\\d+)?,\\s*-?\\d+(?:\\.\\d+)?$")
    }

    fun getRoadAddress(): String {
        // roadAddress, address, gps
        return if (!roadAddress.isNullOrEmpty()) {
            roadAddress
        } else {
            getAddress()
        }
    }

    fun getAddress(): String =
        if (!address.isNullOrEmpty()) {
            address
        } else {
            getGpsAddress()
        }

    fun getGpsAddress(): String = "$latitude,$longitude"
}
