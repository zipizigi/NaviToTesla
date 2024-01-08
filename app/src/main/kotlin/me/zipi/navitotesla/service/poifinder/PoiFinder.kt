package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.DuplicatePoiException
import me.zipi.navitotesla.model.Poi
import java.io.IOException

interface PoiFinder {
    @Throws(DuplicatePoiException::class, IOException::class)
    suspend fun findPoiAddress(poiName: String): String {
        val listPoi = listPoiAddress(poiName)
        var address = ""
        var sameCount = 0
        for (poi in listPoi) {
            if (poi.poiName.equals(poiName, ignoreCase = true)) {
                sameCount++
                address = poi.getRoadAddress()
            }
        }
        if (sameCount > 1) {
            // 중복지명 전송 안함
            throw DuplicatePoiException(poiName)
        }
        return address
    }

    fun parseDestination(notificationText: String): String

    @Throws(IOException::class)
    suspend fun listPoiAddress(poiName: String): List<Poi>
    fun isIgnore(notificationTitle: String, notificationText: String): Boolean
}
