package me.zipi.navitotesla.service.place

/**
 * 3사 POI API 의 주소 표기 차이를 흡수. 도로명은 `<시도> <시군구> <도로명> <건물번호>` 로 통일하고,
 * 도로명 토큰이 없으면(지번/좌표) 지번 규칙만 적용해 나머지는 보존.
 *
 * 도로명주소 규격: `시도 시군구 [읍면] 도로명 [지하]본번[-부번][, 상세주소] [(참고항목)]`.
 * 도로명 코드가 시군구 단위로 고유하므로 읍/면 이하를 떼도 유일성이 유지됨.
 */
object RoadAddressNormalizer {
    // 실측(Kakao/TMAP/Naver 응답) 기반. 각 그룹 첫 항목이 canonical.
    internal val SIDO_CANONICAL: Map<String, String> =
        listOf(
            listOf("서울", "서울특별시", "서울시"),
            listOf("부산", "부산광역시", "부산시"),
            listOf("대구", "대구광역시", "대구시"),
            listOf("인천", "인천광역시", "인천시"),
            listOf("대전", "대전광역시", "대전시"),
            listOf("울산", "울산광역시", "울산시"),
            listOf("세종", "세종특별자치시", "세종시"),
            listOf("경기", "경기도"),
            listOf("강원", "강원도", "강원특별자치도"),
            listOf("충북", "충청북도"),
            listOf("충남", "충청남도"),
            listOf("전북", "전라북도", "전북특별자치도"),
            listOf("경북", "경상북도"),
            listOf("경남", "경상남도"),
            listOf("제주", "제주도", "제주특별자치도"),
            // 통합 이전 표기(광주/전남)도 같은 canonical 로 모음 — Places 응답·레거시 키가 구 표기로 남아 있음.
            // "광주시" 는 경기도 광주시와 구분되지 않아 alias 에서 제외.
            listOf("전남광주", "전남광주통합특별시", "전남광주통합시", "광주", "광주광역시", "전남", "전라남도"),
        ).flatMap { group -> group.map { it to group.first() } }.toMap()

    // 좌표는 쉼표가 구분자라 상세주소 처리 대상에서 제외.
    private val COORDS = Regex("""^-?\d+(?:\.\d+)?,\s*-?\d+(?:\.\d+)?$""")
    private val PAREN = Regex("""\([^)]*\)""")
    private val WHITESPACE = Regex("\\s+")
    private val BUILDING_NO = Regex("""^\d+(?:-\d+)?$""")
    private val SAN_PREFIXED = Regex("""^산(\d.*)$""")
    private val UNDERGROUND_PREFIXED = Regex("""^지하(\d+(?:-\d+)?)$""")

    private data class Cut(
        val idx: Int,
        val onRoad: Boolean,
    )

    /** 캐시 키용. 표기를 canonical 로 바꾸고 건물번호·번지까지만 남김. */
    fun normalize(address: String): String {
        val input = address.trim()
        if (COORDS.matches(input)) return input
        val tokens = splitTokens(input)
        if (tokens.isEmpty()) return ""

        val canon = tokens.toMutableList()
        canon[0] = SIDO_CANONICAL[canon[0]] ?: canon[0]

        val cut = findNumber(canon) ?: return canon.joinToString(" ")
        val raw = canon[cut.idx]
        val number = UNDERGROUND_PREFIXED.matchEntire(raw)?.groupValues?.get(1) ?: raw
        val head =
            if (cut.onRoad) {
                // 읍/면은 시군구 단위 고유성에 영향이 없어 제거, 동/리는 규격상 본 주소에 오지 않는 노이즈.
                // "지하" 도 제거해 3사 표기를 맞춤. 지번은 읍/면을 3사 모두 포함하므로 유지.
                canon.take(cut.idx).filterNot { it.isSubDistrict() || it == "지하" }
            } else {
                canon.take(cut.idx)
            }
        // TMAP 만 번지 없는 곳에 "0" 을 붙임 → 번호 자체를 버림.
        return (if (number == "0") head else head + number).joinToString(" ")
    }

    /** 차량 전송용. 원문 표기(시도·읍면·"지하")를 유지하고 건물번호·번지 뒤 상세주소만 절단. */
    fun trimDetail(address: String): String {
        val input = address.trim()
        if (COORDS.matches(input)) return input
        val tokens = splitTokens(input)
        val cut = findNumber(tokens) ?: return input
        val end = if (tokens[cut.idx] == "0") cut.idx else cut.idx + 1
        return tokens.take(end).joinToString(" ")
    }

    /**
     * 정규화 결과가 특정 지점을 가리키는지. 건물번호·번지가 없으면 시군구/동 단위라 캐시 키로 부적합
     * (예: "경기 여주시", "부산 해운대구 우동").
     */
    fun isSpecific(normalized: String): Boolean = BUILDING_NO.matches(normalized.substringAfterLast(' ', ""))

    // 괄호(참고항목)·쉼표(상세주소 구분)를 토큰 경계로 만들고 "산1-1" 을 분리.
    private fun splitTokens(input: String): List<String> =
        PAREN
            .replace(input, " ")
            .replace(",", " ")
            .replace(WHITESPACE, " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .flatMap { token ->
                SAN_PREFIXED.matchEntire(token)?.let { listOf("산", it.groupValues[1]) } ?: listOf(token)
            }

    /**
     * 건물번호·번지 토큰 위치. 도로명 뒤를 먼저 보고, 없으면 지번 규칙으로 재탐색
     * (예: "언주로 30길 21" 은 도로명 뒤가 번호가 아님).
     * 번호가 첫 토큰이면 `시도 시군구 …` 어순이 아니므로(Places 의 쉼표·라틴 표기, 시도 생략 입력) null.
     */
    private fun findNumber(tokens: List<String>): Cut? {
        val roadIdx = tokens.indexOfFirst { it.isRoadName() }
        if (roadIdx == 0) return null
        if (roadIdx > 0) {
            var i = roadIdx + 1
            if (tokens.getOrNull(i) == "지하") i++
            val token = tokens.getOrNull(i)
            if (token != null && (BUILDING_NO.matches(token) || UNDERGROUND_PREFIXED.matches(token))) {
                return Cut(i, onRoad = true)
            }
        }
        val jibunIdx = tokens.indexOfFirst { BUILDING_NO.matches(it) }
        return if (jibunIdx > 0) Cut(jibunIdx, onRoad = false) else null
    }

    private fun String.isRoadName(): Boolean = endsWith("로") || endsWith("길")

    private fun String.isSubDistrict(): Boolean = endsWith("읍") || endsWith("면") || endsWith("동") || endsWith("리")
}
