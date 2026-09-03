package me.zipi.navitotesla.service.place

import com.google.android.libraries.places.api.model.AutocompletePrediction

/**
 * 도로명 주소 매칭 룰. 가장 앞부분을 제외하고 포함여부로 한다. 서울시/경기도 등을 제외한다. (특별시, 특별자치시 등)
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

        // 현행 PoiFinder 는 괄호 토큰 미생성 — 레거시 즐겨찾기·직접 입력 대비로 유지.
        private val PAREN_NOISE = Regex("""\([^)]*\)""")
    }
}
