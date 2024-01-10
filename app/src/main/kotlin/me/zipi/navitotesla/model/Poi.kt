package me.zipi.navitotesla.model

import java.util.Locale

data class Poi(
    var poiName: String? = null,
    private var roadAddress: String? = null,
    private var address: String? = null,
    var longitude: String? = null,
    var latitude: String? = null,
) {
    @Suppress("unused")
    fun isAddressEmpty(): Boolean = (roadAddress == null || roadAddress == "") && (address == null || address == "")

    fun getRoadAddress(): String {
        // roadAddress, address, gps
        return if (roadAddress != null && roadAddress!!.isNotEmpty()) {
            roadAddress!!
        } else {
            getAddress()
        }
    }

    fun getAddress(): String {
        return if (address != null && address!!.isNotEmpty()) {
            address!!
        } else {
            getGpsAddress()
        }
    }

    fun getGpsAddress(): String = String.format(Locale.getDefault(), "%s,%s", latitude, longitude)
}
