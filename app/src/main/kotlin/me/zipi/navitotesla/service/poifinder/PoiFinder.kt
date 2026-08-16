package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.DuplicatePoiException
import me.zipi.navitotesla.model.Poi
import java.io.IOException

interface PoiFinder {
    @Throws(DuplicatePoiException::class, IOException::class)
    suspend fun findPoi(
        poiName: String,
        packageName: String = "",
    ): Poi {
        val poiList = listPoiAddress(poiName).filter { it.poiName.equals(poiName, ignoreCase = true) }

        return if (poiList.size > 1) {
            throw DuplicatePoiException(poiName, poiList.map { it.copy(isDuplicate = true, packageName = packageName) })
        } else if (poiList.size == 1) {
            poiList[0].copy(packageName = packageName)
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

    /** 전송에 성공하면 접근성으로 모아 둔 목적지를 버린다. 알림이 재발행돼도 다시 나가지 않도록. */
    fun consumeCapturedDestination() {}
}
