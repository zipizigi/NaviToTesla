package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.model.ShareTransport
import me.zipi.navitotesla.service.place.Searchability
import me.zipi.navitotesla.util.AnalysisUtil
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
        val safeSettings = sanitizeSettings(settings)
        // 1. 즐겨찾기 explicit 선택 우선
        if (registeredSentMode != null) {
            return planRegistered(poi, registeredSentMode, safeSettings)
        }

        // 2. UNKNOWN → RC 에 따라 NotSearchable 로 승격
        val effectiveSearchability =
            if (safeSettings.treatUnknownAsNotSearchable && searchability is Searchability.Unknown) {
                Searchability.NotSearchable
            } else {
                searchability
            }

        // 3. 모드 결정
        var mode =
            if (effectiveSearchability is Searchability.NotSearchable) {
                safeSettings.fallbackMode
            } else {
                safeSettings.defaultMode
            }
        if (isDuplicateSelected && mode == SendMode.NAME) {
            mode = SendMode.ROAD
        }

        val rawByMode =
            when (mode) {
                SendMode.ROAD -> poi.getRoadAddress()
                SendMode.JIBUN -> jibunOrRoad(poi)
                SendMode.NAME -> poi.poiName ?: poi.getRoadAddress()
                // sanitizeSettings 가 GPS 를 ROAD 로 강등하므로 정상 흐름에서는 도달하지 않음.
                // 안전망으로 road 폴백을 둔다 (when 을 exhaustive 하게 유지).
                SendMode.GPS -> poi.getRoadAddress()
            }

        val byAppNonKorean = safeSettings.shareTransport == ShareTransport.APP &&
            safeSettings.locale.language != "ko"
        val viaUrl = mode == SendMode.NAME ||
            effectiveSearchability is Searchability.NotSearchable ||
            byAppNonKorean
        val sendText =
            if (viaUrl) GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(rawByMode, "UTF-8") else rawByMode
        val displayText =
            if (mode == SendMode.NAME) poi.poiName ?: rawByMode else rawByMode

        return SendPayload(sendText = sendText, displayText = displayText, mode = mode, viaUrl = viaUrl)
    }

    private fun sanitizeSettings(settings: SendSettings): SendSettings {
        val needsFix = settings.defaultMode == SendMode.GPS || settings.fallbackMode == SendMode.GPS
        if (!needsFix) return settings
        AnalysisUtil.warn("GPS Mode not support. change to Road")
        return settings.copy(
            defaultMode = if (settings.defaultMode == SendMode.GPS) SendMode.ROAD else settings.defaultMode,
            fallbackMode = if (settings.fallbackMode == SendMode.GPS) SendMode.ROAD else settings.fallbackMode,
        )
    }

    private fun planRegistered(
        poi: Poi,
        sentMode: String,
        settings: SendSettings,
    ): SendPayload {
        val byAppNonKorean = settings.shareTransport == ShareTransport.APP &&
            settings.locale.language != "ko"
        return when (sentMode) {
            PoiAddressEntity.SENT_MODE_JIBUN -> {
                val jibun = jibunOrRoad(poi)
                wrapIfNeeded(jibun, jibun, SendMode.JIBUN, byAppNonKorean)
            }
            PoiAddressEntity.SENT_MODE_GPS -> {
                if (!poi.latitude.isNullOrBlank() && !poi.longitude.isNullOrBlank()) {
                    val gps = "${poi.latitude},${poi.longitude}"
                    // GPS branch is locale-neutral — coordinates work regardless.
                    SendPayload(gps, gps, SendMode.GPS, viaUrl = false)
                } else {
                    val road = poi.getRoadAddress()
                    wrapIfNeeded(road, road, SendMode.ROAD, byAppNonKorean)
                }
            }
            else -> {
                val road = poi.getRoadAddress()
                wrapIfNeeded(road, road, SendMode.ROAD, byAppNonKorean)
            }
        }
    }

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
