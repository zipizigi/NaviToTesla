package me.zipi.navitotesla.model

import java.util.Locale

data class Poi(
    val poiName: String? = null,
    private val roadAddress: String? = null,
    private val address: String? = null,
    val longitude: String? = null,
    val latitude: String? = null,
) {
    @Suppress("unused")
    fun isAddressEmpty(): Boolean = (roadAddress == null || roadAddress == "") && (address == null || address == "")

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

    fun getGpsAddress(): String = String.format(Locale.getDefault(), "%s,%s", latitude, longitude)
}
