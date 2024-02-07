package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.DuplicatePoiException
import me.zipi.navitotesla.model.Poi
import java.io.IOException

interface PoiFinder {
    @Throws(DuplicatePoiException::class, IOException::class)
    suspend fun findPoi(poiName: String): Poi {
        val poiList = listPoiAddress(poiName).filter { it.poiName.equals(poiName, ignoreCase = true) }

        return if (poiList.size > 1) {
            throw DuplicatePoiException(poiName)
        } else if (poiList.size == 1) {
            poiList[0]
        } else {
            Poi()
        }
    }

    fun parseDestination(notificationText: String): String

    @Throws(IOException::class)
    suspend fun listPoiAddress(poiName: String): List<Poi>

    fun isIgnore(
        notificationTitle: String,
        notificationText: String,
    ): Boolean
}
