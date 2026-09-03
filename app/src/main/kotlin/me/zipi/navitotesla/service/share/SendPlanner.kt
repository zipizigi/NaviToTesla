package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.model.ShareTransport
import me.zipi.navitotesla.service.place.RoadAddressNormalizer
import me.zipi.navitotesla.service.place.Searchability
import java.net.URLEncoder

object SendPlanner {
    private const val GOOGLE_MAPS_URL_PREFIX = "https://maps.google.com/maps?q="

    fun plan(
        poi: Poi,
        searchability: Searchability,
        isDuplicateSelected: Boolean,
        settings: SendSettings,
    ): SendPayload {
        // 좌표 형식이면 raw GPS payload — URL wrap 없이 Tesla 에 좌표 직접 전달.
        if (poi.isCoordsAddress()) {
            val coords = poi.getRoadAddress().trim()
            return SendPayload(sendText = coords, displayText = coords, mode = SendMode.GPS, viaUrl = false)
        }

        // UNKNOWN → RC 에 따라 NotSearchable 로 승격
        val effectiveSearchability =
            if (settings.treatUnknownAsNotSearchable && searchability is Searchability.Unknown) {
                Searchability.NotSearchable
            } else {
                searchability
            }

        var mode =
            if (effectiveSearchability is Searchability.NotSearchable) {
                settings.fallbackMode
            } else {
                settings.defaultMode
            }
        // 즐겨찾기는 사용자가 등록한 roadAddress 텍스트가 곧 destination — NAME 분기를 우회.
        if (poi.isFavorite && mode == SendMode.NAME) {
            mode = SendMode.ROAD
        }
        if (isDuplicateSelected && mode == SendMode.NAME) {
            mode = SendMode.ROAD
        }
        // GPS 모드 이지만 좌표 없으면 ROAD 로 강등
        if (mode == SendMode.GPS && !hasCoords(poi)) {
            mode = SendMode.ROAD
        }
        // 주소가 시군구/동 단위면(예: "경기 여주시") 목적지를 특정하지 못함 → 지번, 없으면 좌표로 강등.
        var demotedToCoords = false
        if (addressOf(mode, poi)?.let { !isSpecific(it) } == true) {
            if (mode == SendMode.ROAD && isSpecific(jibunOrRoad(poi))) {
                mode = SendMode.JIBUN
            } else if (hasCoords(poi)) {
                mode = SendMode.GPS
                demotedToCoords = true
            }
        }

        // Searchable 판정은 정규화된 주소로 했으므로 전송도 같은 문자열이어야 함.
        // 원문을 그대로 보내면 상세주소 꼬리("… 513 지하1층 O-107호 라운지") 때문에 차량 검색이 실패한다.
        // 즐겨찾기는 사용자가 등록한 텍스트가 곧 destination 이라 예외.
        val rawByMode =
            when (mode) {
                SendMode.ROAD -> poi.getRoadAddress().normalizedUnlessFavorite(poi)
                SendMode.JIBUN -> jibunOrRoad(poi).normalizedUnlessFavorite(poi)
                SendMode.NAME -> poi.poiName ?: poi.getRoadAddress()
                SendMode.GPS -> "${poi.latitude},${poi.longitude}"
            }

        val byAppNonKorean =
            settings.shareTransport == ShareTransport.APP &&
                settings.locale.language != "ko"
        // GPS 좌표는 locale-neutral 이고 그 자체가 destination 이라 wrap 하지 않는다.
        val viaUrl =
            mode != SendMode.GPS &&
                (
                    mode == SendMode.NAME ||
                        effectiveSearchability is Searchability.NotSearchable ||
                        byAppNonKorean
                )
        val sendText =
            if (viaUrl) GOOGLE_MAPS_URL_PREFIX + URLEncoder.encode(rawByMode, "UTF-8") else rawByMode
        // 자동으로 좌표로 강등된 경우 토스트에 좌표만 보이면 알아볼 수 없어 POI 이름을 쓴다.
        val displayText =
            if (mode == SendMode.NAME || demotedToCoords) poi.poiName ?: rawByMode else rawByMode

        return SendPayload(sendText = sendText, displayText = displayText, mode = mode, viaUrl = viaUrl)
    }

    private fun String.normalizedUnlessFavorite(poi: Poi): String = if (poi.isFavorite) this else RoadAddressNormalizer.trimDetail(this)

    private fun hasCoords(poi: Poi): Boolean = !poi.latitude.isNullOrBlank() && !poi.longitude.isNullOrBlank()

    // NAME 은 주소가 아니고 GPS 는 이미 좌표라 완전성 판정 대상이 아님.
    private fun addressOf(
        mode: SendMode,
        poi: Poi,
    ): String? =
        when (mode) {
            SendMode.ROAD -> poi.getRoadAddress()
            SendMode.JIBUN -> jibunOrRoad(poi)
            SendMode.NAME, SendMode.GPS -> null
        }

    private fun isSpecific(address: String): Boolean = RoadAddressNormalizer.isSpecific(RoadAddressNormalizer.normalize(address))

    // Poi.getAddress() 는 jibun 이 비어있으면 GPS 좌표로 폴백하므로,
    // 좌표 폴백을 감지해 road 로 다시 폴백한다.
    private fun jibunOrRoad(poi: Poi): String {
        val jibun = poi.getAddress()
        val isAddressEmpty =
            jibun.isEmpty() ||
                jibun == poi.getGpsAddress() ||
                jibun == "null,null"
        return if (isAddressEmpty) poi.getRoadAddress() else jibun
    }
}
