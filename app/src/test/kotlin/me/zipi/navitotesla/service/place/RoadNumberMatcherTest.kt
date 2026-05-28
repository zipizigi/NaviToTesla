package me.zipi.navitotesla.service.place

import android.text.SpannableString
import com.google.android.libraries.places.api.model.AutocompletePrediction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadNumberMatcherTest {
    private val matcher = RoadNumberMatcher()

    private fun prediction(fullText: String): AutocompletePrediction {
        val spannable = mockk<SpannableString>()
        every { spannable.toString() } returns fullText
        val p = mockk<AutocompletePrediction>()
        every { p.getFullText(null) } returns spannable
        return p
    }

    private fun assertMatch(
        input: String,
        description: String,
    ) {
        assertTrue(
            "expected match: '$input' vs '$description'",
            matcher.matches(input, listOf(prediction(description))),
        )
    }

    private fun assertNoMatch(
        input: String,
        description: String,
    ) {
        assertFalse(
            "expected no match: '$input' vs '$description'",
            matcher.matches(input, listOf(prediction(description))),
        )
    }

    // === 기본 케이스 ===

    @Test
    fun `description 에 번지가 없으면 no match`() {
        assertNoMatch(
            input = "서울특별시 중구 세종대로 110",
            description = "대한민국 서울특별시 중구 세종대로",
        )
    }

    @Test
    fun `도로명+번지 정확히 일치하면 match`() {
        assertMatch(
            input = "서울특별시 중구 세종대로 31",
            description = "대한민국 서울특별시 중구 세종대로 31",
        )
    }

    @Test
    fun `prediction 목록이 비어 있으면 no match`() {
        assertFalse(matcher.matches("경기도 어디시 어디로 1", emptyList()))
    }

    @Test
    fun `시도명 한 토큰만 있으면 no match`() {
        assertNoMatch(
            input = "서울특별시",
            description = "대한민국 서울특별시 중구 세종대로 31",
        )
    }

    // === 사용자 제공 14개 케이스 ===

    @Test
    fun `case01 세종특별시 입력 vs 세종특별자치시 응답 match`() {
        assertMatch(
            input = "세종특별시 정안세종로 1527",
            description = "대한민국 세종특별자치시 정안세종로 1527",
        )
    }

    @Test
    fun `case02 세종시 줄임형도 match`() {
        assertMatch(
            input = "세종시 정안세종로 1527",
            description = "대한민국 세종특별자치시 정안세종로 1527",
        )
    }

    @Test
    fun `case03 시도명 없는 입력도 도로명+번지가 응답에 있으면 match`() {
        assertMatch(
            input = "정안세종로 1527",
            description = "대한민국 세종특별자치시 정안세종로 1527",
        )
    }

    @Test
    fun `case04 POI 이름 포함 + 응답에서 번지 빠지면 no match (다른 번지 안내 위험)`() {
        assertNoMatch(
            input = "제주특별자치도 제주시 광양9길 10 제주시청",
            description = "대한민국 제주특별자치도 제주시 특별자치도 광양9길 제주시청",
        )
    }

    @Test
    fun `case05 제주특별자치도 정식명 + 번지 동일 match`() {
        assertMatch(
            input = "제주특별자치도 제주시 광양9길 10",
            description = "대한민국 제주특별자치도 제주시 광양9길 10",
        )
    }

    @Test
    fun `case06 제주도 줄임형도 match`() {
        assertMatch(
            input = "제주도 제주시 광양9길 10",
            description = "대한민국 제주도 제주시 광양9길 10",
        )
    }

    @Test
    fun `case07 제주시 중복 입력도 도 제거 후 매칭`() {
        assertMatch(
            input = "제주시 제주시 광양9길 10",
            description = "대한민국 제주특별자치도 제주시 광양9길 10",
        )
    }

    @Test
    fun `case08 시 단독 입력도 응답에 부분 포함되면 match`() {
        assertMatch(
            input = "제주시 광양9길 10",
            description = "대한민국 제주특별자치도 제주시 광양9길 10",
        )
    }

    @Test
    fun `case09 전북특별자치도 정식명 + POI + 응답 번지 빠짐 = no match`() {
        assertNoMatch(
            input = "전북특별자치도 익산시 인북로32길 1 익산시청",
            description = "대한민국 전북특별자치도 익산시 인북로32길 익산시청",
        )
    }

    @Test
    fun `case10 전북 줄임형 + POI + 번지 빠짐 = no match`() {
        assertNoMatch(
            input = "전북 익산시 인북로32길 1 익산시청",
            description = "대한민국 전북특별자치도 익산시 인북로32길 익산시청",
        )
    }

    // === 번지 다른 응답 = no match (token-subset 알고리즘 — 같은 도로 다른 번지는 다른 토큰) ===

    @Test
    fun `case11 입력 번지가 응답과 다르면 no match (다른 번지 안내 위험)`() {
        // 토큰 "1" 이 응답 토큰 집합 {"18-12", ...} 에 없음 → no match.
        // 차량에 도로명+다른번지가 전송되어 잘못된 위치로 안내될 위험을 회피.
        assertNoMatch(
            input = "전북 익산시 인북로32길 1",
            description = "대한민국 전북 익산시 인북로32길 18-12",
        )
    }

    @Test
    fun `case12 전라북도 정식명 - 번지 다르면 no match`() {
        assertNoMatch(
            input = "전라북도 익산시 인북로32길 1",
            description = "대한민국 전라북도 익산시 인북로32길 18-12",
        )
    }

    @Test
    fun `case13 시도명 생략 - 번지 다르면 no match`() {
        assertNoMatch(
            input = "익산시 인북로32길 1",
            description = "대한민국 전북특별자치도 익산시 인북로32길 18-12",
        )
    }

    @Test
    fun `case14 도로명+번지 단독 - 번지 다르면 no match`() {
        assertNoMatch(
            input = "인북로32길 1",
            description = "대한민국 전북특별자치도 익산시 인북로32길 18-12",
        )
    }

    // === 시군구 추가/제거 (token subset 으로 OK) ===

    @Test
    fun `입력에 시군구 없고 응답에는 있어도 match (세종대로 109 케이스)`() {
        // PoiFinder 가 도로명 끝에 (동이름) 부가 토큰 + 응답에 "중구" 추가 끼어 있는 실제 케이스.
        assertMatch(
            input = "서울특별시 세종대로 109 (태평로1가)",
            description = "대한민국 서울특별시 중구 세종대로 109",
        )
    }

    @Test
    fun `응답에 부가 시군구 토큰이 끼어 있어도 token subset 으로 match`() {
        assertMatch(
            input = "서울특별시 세종대로 109",
            description = "대한민국 서울특별시 중구 세종대로 109",
        )
    }

    // === 괄호 부가 토큰 정규화 (PoiFinder withLocalName 결과) ===

    @Test
    fun `괄호 동이름 부가 토큰은 정규화로 제거됨`() {
        assertMatch(
            input = "서울특별시 중구 세종대로 31 (태평로1가)",
            description = "대한민국 서울특별시 중구 세종대로 31",
        )
    }

    @Test
    fun `응답에 괄호 토큰이 있고 입력에는 없어도 정규화로 동일하게 처리`() {
        assertMatch(
            input = "서울특별시 중구 세종대로 31",
            description = "대한민국 서울특별시 중구 세종대로 31 (태평로1가)",
        )
    }

    // === 도로명 종류별 (대로/로/길) ===

    @Test
    fun `대로 도로명 매치`() {
        assertMatch(
            input = "서울특별시 강남구 영동대로 513",
            description = "대한민국 서울특별시 강남구 영동대로 513",
        )
    }

    @Test
    fun `숫자 포함 길 도로명 매치`() {
        assertMatch(
            input = "제주시 광양9길 10",
            description = "대한민국 제주특별자치도 제주시 광양9길 10",
        )
    }

    @Test
    fun `로뒤 숫자 포함 도로명 매치`() {
        assertMatch(
            input = "익산시 인북로32길 18-12",
            description = "대한민국 전북특별자치도 익산시 인북로32길 18-12",
        )
    }

    // === 광역시 / 특별시 매치 ===

    @Test
    fun `광역시 정식명 매치`() {
        assertMatch(
            input = "부산광역시 해운대구 해운대로 264",
            description = "대한민국 부산광역시 해운대구 해운대로 264",
        )
    }

    @Test
    fun `서울시 줄임형 매치`() {
        assertMatch(
            input = "서울시 강남구 테헤란로 152",
            description = "대한민국 서울시 강남구 테헤란로 152",
        )
    }

    // === 상세주소 / POI 가 양쪽에 있으면 match ===

    @Test
    fun `입력에 동 같은 상세주소가 있고 응답에도 같이 있으면 match`() {
        assertMatch(
            input = "서울특별시 세종대로 31 상가동",
            description = "대한민국 서울특별시 세종대로 31 상가동",
        )
    }

    // === 공백 정규화 ===

    @Test
    fun `공백이 여러 개여도 정규화 후 매치`() {
        assertMatch(
            input = "서울시 강남구  영동대로 513",
            description = "대한민국  서울시   강남구 영동대로 513",
        )
    }
}
