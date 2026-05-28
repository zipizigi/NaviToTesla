package me.zipi.navitotesla.model

data class Poi(
    val poiName: String? = null,
    private val roadAddress: String? = null,
    private val address: String? = null,
    val longitude: String? = null,
    val latitude: String? = null,
    val packageName: String = "",
    val isDuplicate: Boolean = false,
    /** non-null = registered favorite 의 sentMode (ROAD/JIBUN/GPS). null = favorite 아님. */
    val registeredSentMode: String? = null,
) {
    @Suppress("unused")
    fun isAddressEmpty(): Boolean = roadAddress.isNullOrEmpty() && address.isNullOrEmpty()

    @Suppress("unused")
    fun isGpsEmpty(): Boolean = (longitude == null || latitude == null)

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
