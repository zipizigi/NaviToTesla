package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.model.ShareTransport
import me.zipi.navitotesla.service.place.Searchability
import java.net.URLEncoder

object SendPlanner {
    private const val GOOGLE_MAPS_URL_PREFIX = "https://maps.google.com/maps?q="

    fun plan(
        poi: Poi,
        searchability: Searchability,
        registeredSentMode: String?,
        isDuplicateSelected: Boolean,
        settings: SendSettings,
    ): SendPayload {
        // 1. 즐겨찾기 explicit 선택 우선
        if (registeredSentMode != null) {
            return planRegistered(poi, registeredSentMode)
        }

        // 2. UNKNOWN → RC 에 따라 NotSearchable 로 승격
        val effectiveSearchability =
            if (settings.treatUnknownAsNotSearchable && searchability is Searchability.Unknown) {
                Searchability.NotSearchable
            } else {
                searchability
            }

        // 3. 모드 결정
        var mode =
            if (effectiveSearchability is Searchability.NotSearchable) {
                settings.fallbackMode
            } else {
                settings.defaultMode
            }
        if (isDuplicateSelected && mode == SendMode.NAME) {
            mode = SendMode.ROAD
        }

        val rawByMode =
            when (mode) {
                SendMode.ROAD -> poi.getRoadAddress()
                SendMode.JIBUN -> jibunOrRoad(poi)
                SendMode.NAME -> poi.poiName ?: poi.getRoadAddress()
                SendMode.GPS -> error("GPS is registered-only and must not appear in settings")
            }

        val byAppNonKorean = settings.shareTransport == ShareTransport.APP &&
            settings.locale.language != "ko"
        val viaUrl = mode == SendMode.NAME ||
            effectiveSearchability is Searchability.NotSearchable ||
            byAppNonKorean
        val sendText =
            if (viaUrl) GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(rawByMode, "UTF-8") else rawByMode
        val displayText =
            if (mode == SendMode.NAME) poi.poiName ?: rawByMode else rawByMode

        return SendPayload(sendText = sendText, displayText = displayText, mode = mode, viaUrl = viaUrl)
    }

    private fun planRegistered(
        poi: Poi,
        sentMode: String,
    ): SendPayload =
        when (sentMode) {
            PoiAddressEntity.SENT_MODE_JIBUN -> {
                val jibun = jibunOrRoad(poi)
                SendPayload(jibun, jibun, SendMode.JIBUN, viaUrl = false)
            }
            PoiAddressEntity.SENT_MODE_GPS -> {
                if (poi.latitude != null && poi.longitude != null) {
                    val gps = "${poi.latitude},${poi.longitude}"
                    SendPayload(gps, gps, SendMode.GPS, viaUrl = false)
                } else {
                    val road = poi.getRoadAddress()
                    SendPayload(road, road, SendMode.ROAD, viaUrl = false)
                }
            }
            else -> {
                val road = poi.getRoadAddress()
                SendPayload(road, road, SendMode.ROAD, viaUrl = false)
            }
        }

    // Poi.getAddress() 는 jibun 이 비어있으면 GPS 좌표로 폴백하므로,
    // 좌표 폴백을 감지해 road 로 다시 폴백한다.
    private fun jibunOrRoad(poi: Poi): String {
        val jibun = poi.getAddress()
        val gpsFallback = poi.latitude != null && poi.longitude != null && jibun == poi.getGpsAddress()
        return if (jibun.isEmpty() || gpsFallback) poi.getRoadAddress() else jibun
    }
}
