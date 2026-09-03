package me.zipi.navitotesla.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 필드 값은 TMAP POI API 실제 응답 실측값. */
class TMapTest {
    private fun poi(
        upper: String? = null,
        middle: String? = null,
        road: String? = null,
        buildNo1: String? = null,
        buildNo2: String? = null,
    ) = TMap.PoiItem(
        upperAddrName = upper,
        middleAddrName = middle,
        roadName = road,
        firstBuildNo = buildNo1,
        secondBuildNo = buildNo2,
    )

    @Test
    fun `건물부번을 붙임 - 가평역`() {
        val item = poi(upper = "경기", middle = "가평군", road = "문화로", buildNo1 = "13", buildNo2 = "42")
        assertEquals("경기 가평군 문화로 13-42", item.getRoadAddress())
    }

    @Test
    fun `건물부번을 붙임 - 여수해상케이블카`() {
        val item = poi(upper = "전남광주", middle = "여수시", road = "돌산로", buildNo1 = "3600", buildNo2 = "1")
        assertEquals("전남광주 여수시 돌산로 3600-1", item.getRoadAddress())
    }

    @Test
    fun `부번 0 은 생략`() {
        val item = poi(upper = "서울", middle = "중구", road = "세종대로", buildNo1 = "110", buildNo2 = "0")
        assertEquals("서울 중구 세종대로 110", item.getRoadAddress())
    }

    @Test
    fun `부번이 빈 문자열이면 생략`() {
        val item = poi(upper = "서울", middle = "강남구", road = "영동대로", buildNo1 = "513", buildNo2 = "")
        assertEquals("서울 강남구 영동대로 513", item.getRoadAddress())
    }

    @Test
    fun `부번이 null 이면 생략`() {
        val item = poi(upper = "서울", middle = "강남구", road = "영동대로", buildNo1 = "513")
        assertEquals("서울 강남구 영동대로 513", item.getRoadAddress())
    }

    @Test
    fun `시군구가 없으면 시도 다음에 도로명 - 세종`() {
        val item = poi(upper = "세종특별자치시", road = "다솜2로", buildNo1 = "94")
        assertEquals("세종특별자치시 다솜2로 94", item.getRoadAddress())
    }

    /** 읍/면과 "지하" 는 조립에 포함되지 않음. 캐시 키는 정규화가 흡수하므로 3사 수렴에 영향 없음. */
    @Test
    fun `읍면과 지하는 조립에 포함되지 않음`() {
        val eup = poi(upper = "경북", middle = "포항시 북구", road = "포항역로", buildNo1 = "1")
        assertEquals("경북 포항시 북구 포항역로 1", eup.getRoadAddress())

        val underground = poi(upper = "경기", middle = "성남시 분당구", road = "성남대로", buildNo1 = "333")
        assertEquals("경기 성남시 분당구 성남대로 333", underground.getRoadAddress())
    }

    @Test
    fun `도로명이나 건물번호가 없으면 빈 문자열 - 지번 폴백 유도`() {
        assertEquals("", poi(upper = "경기", middle = "안산시 단원구").getRoadAddress())
        assertEquals("", poi(upper = "서울", middle = "중구", road = "세종대로").getRoadAddress())
        assertEquals("", poi(upper = "서울", middle = "중구", buildNo1 = "110").getRoadAddress())
    }
}
