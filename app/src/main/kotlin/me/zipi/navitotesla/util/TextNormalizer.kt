package me.zipi.navitotesla.util

/** 목적지에 섞여 오는 비표준 공백·하이픈을 표준 문자로 접는다. */
object TextNormalizer {
    private val SPACES =
        charArrayOf(
            '\u00A0',
            '\u2000',
            '\u2001',
            '\u2002',
            '\u2003',
            '\u2004',
            '\u2005',
            '\u2006',
            '\u2007',
            '\u2008',
            '\u2009',
            '\u200A',
            '\u202F',
            '\u205F',
            '\u3000',
        )

    private val HYPHENS =
        charArrayOf(
            '\u2010',
            '\u2011',
            '\u2012',
            '\u2013',
            '\u2014',
            '\u2212',
            '\uFF0D',
        )

    private val INVISIBLE =
        charArrayOf(
            '\u200B',
            '\u200C',
            '\u200D',
            '\uFEFF',
        )

    private val MULTI_SPACE = Regex("\\s+")

    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            when {
                INVISIBLE.contains(c) -> Unit
                SPACES.contains(c) -> sb.append(' ')
                HYPHENS.contains(c) -> sb.append('-')
                else -> sb.append(c)
            }
        }
        return MULTI_SPACE.replace(sb, " ").trim()
    }
}
