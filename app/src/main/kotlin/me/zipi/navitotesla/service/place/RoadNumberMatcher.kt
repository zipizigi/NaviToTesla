package me.zipi.navitotesla.service.place

import com.google.android.libraries.places.api.model.AutocompletePrediction

/**
 * 한국 도로명 주소 매처 (lenient).
 *
 * navi 응답은 보통 `시도 + 시군구 + 도로명 + 번지` 형식이지만 응답에 따라:
 *   - 시도명 표기가 다르거나 (제주도 ↔ 제주특별자치도, 전북 ↔ 전북특별자치도)
 *   - 시군구 추가 (입력 "용인시 구갈로 51" ↔ 응답 "용인시 기흥구 구갈로 51")
 *   - 괄호 부가 토큰 (입력 "구갈로 51 (구갈동)" — PoiFinder 의 withLocalName 결과)
 * 같은 변형이 발생.
 *
 * 알고리즘:
 *   1. 입력/응답 모두 토큰화 + 괄호 토큰 제거 + 빈 토큰 제거
 *   2. 입력 첫 토큰 (시도명 가정) 제거
 *   3. 남은 입력 토큰이 응답 토큰 집합에 **모두 포함** 되면 매치 (순서 무관, 중간 토큰 끼어 있어도 OK)
 *
 * 사전 없이 토큰 비교만 하므로 행정구역 명칭 변경에도 robust. 너프 정책 — 도로명 주소가 가능한 한
 * 차량으로 전송되도록 너그럽게 매치하지만, 번지가 응답에 없거나 다르면 no match (구주소 fallback).
 */
class RoadNumberMatcher {
    fun matches(
        input: String,
        predictions: List<AutocompletePrediction>,
    ): Boolean {
        val tokens = tokenize(input).drop(1)
        if (tokens.isEmpty()) return false
        return predictions.any { p ->
            val descTokens = tokenize(p.getFullText(null).toString()).toSet()
            tokens.all { it in descTokens }
        }
    }

    private fun tokenize(s: String): List<String> =
        s
            .trim()
            .replace(PAREN_NOISE, " ")
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        // PoiFinder.getRoadAddressName(withLocalName=true) 가 도로명 끝에 붙이는 "(동이름)" 형태 부가 토큰 제거.
        private val PAREN_NOISE = Regex("""\([^)]*\)""")
    }
}
