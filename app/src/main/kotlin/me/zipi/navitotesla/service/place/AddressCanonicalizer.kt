package me.zipi.navitotesla.service.place

object AddressCanonicalizer {
    private val WHITESPACE = Regex("\\s+")

    /** 맨 앞 "대한민국" 토큰 제거 + 내부 연속공백 1칸 + trim. 읽기/쓰기 공통 키 정규화. */
    fun canonicalize(address: String): String {
        val collapsed = address.trim().replace(WHITESPACE, " ")
        return when {
            collapsed == "대한민국" -> ""
            collapsed.startsWith("대한민국 ") -> collapsed.removePrefix("대한민국 ")
            else -> collapsed
        }
    }
}
