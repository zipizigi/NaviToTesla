package me.zipi.navitotesla.service.share

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
        registeredSentMode: SendMode?,
        isDuplicateSelected: Boolean,
        settings: SendSettings,
    ): SendPayload {
        // 1. 즐겨찾기 explicit 선택 우선
        if (registeredSentMode != null) {
            return planRegistered(poi, registeredSentMode, settings)
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
        // GPS 모드 이지만 좌표 없으면 ROAD 로 강등
        if (mode == SendMode.GPS && !hasCoords(poi)) {
            mode = SendMode.ROAD
        }

        val rawByMode =
            when (mode) {
                SendMode.ROAD -> poi.getRoadAddress()
                SendMode.JIBUN -> jibunOrRoad(poi)
                SendMode.NAME -> poi.poiName ?: poi.getRoadAddress()
                SendMode.GPS -> "${poi.latitude},${poi.longitude}"
            }

        val byAppNonKorean = settings.shareTransport == ShareTransport.APP &&
            settings.locale.language != "ko"
        // GPS 좌표는 locale-neutral 이고 그 자체가 destination 이라 wrap 하지 않는다.
        val viaUrl = mode != SendMode.GPS &&
            (
                mode == SendMode.NAME ||
                    effectiveSearchability is Searchability.NotSearchable ||
                    byAppNonKorean
            )
        val sendText =
            if (viaUrl) GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(rawByMode, "UTF-8") else rawByMode
        val displayText =
            if (mode == SendMode.NAME) poi.poiName ?: rawByMode else rawByMode

        return SendPayload(sendText = sendText, displayText = displayText, mode = mode, viaUrl = viaUrl)
    }

    private fun planRegistered(
        poi: Poi,
        sentMode: SendMode,
        settings: SendSettings,
    ): SendPayload {
        val byAppNonKorean = settings.shareTransport == ShareTransport.APP &&
            settings.locale.language != "ko"
        return when (sentMode) {
            SendMode.ROAD -> {
                val road = poi.getRoadAddress()
                wrapIfNeeded(road, road, SendMode.ROAD, byAppNonKorean)
            }
            SendMode.JIBUN -> {
                val jibun = jibunOrRoad(poi)
                wrapIfNeeded(jibun, jibun, SendMode.JIBUN, byAppNonKorean)
            }
            SendMode.NAME -> {
                // NAME 은 항상 URL wrap (Tesla 가 raw 명칭을 주소로 인식 못 함). locale 무관.
                val name = poi.poiName ?: poi.getRoadAddress()
                wrapIfNeeded(name, name, SendMode.NAME, wrap = true)
            }
            SendMode.GPS -> {
                if (hasCoords(poi)) {
                    val gps = "${poi.latitude},${poi.longitude}"
                    // GPS 좌표는 locale-neutral — wrap 없이 그대로 전송
                    SendPayload(gps, gps, SendMode.GPS, viaUrl = false)
                } else {
                    val road = poi.getRoadAddress()
                    wrapIfNeeded(road, road, SendMode.ROAD, byAppNonKorean)
                }
            }
        }
    }

    private fun hasCoords(poi: Poi): Boolean =
        !poi.latitude.isNullOrBlank() && !poi.longitude.isNullOrBlank()

    private fun wrapIfNeeded(
        rawSend: String,
        display: String,
        mode: SendMode,
        wrap: Boolean,
    ): SendPayload {
        val sendText = if (wrap) GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(rawSend, "UTF-8") else rawSend
        return SendPayload(sendText, display, mode, viaUrl = wrap)
    }

    // Poi.getAddress() 는 jibun 이 비어있으면 GPS 좌표로 폴백하므로,
    // 좌표 폴백을 감지해 road 로 다시 폴백한다.
    private fun jibunOrRoad(poi: Poi): String {
        val jibun = poi.getAddress()
        // Poi.getAddress() falls back to getGpsAddress() — either "lat,lng" or the literal
        // "null,null" — when the underlying address field is empty. Both are unfit as jibun;
        // re-fall back to road.
        val isAddressEmpty = jibun.isEmpty() ||
            jibun == poi.getGpsAddress() ||
            jibun == "null,null"
        return if (isAddressEmpty) poi.getRoadAddress() else jibun
    }
}
