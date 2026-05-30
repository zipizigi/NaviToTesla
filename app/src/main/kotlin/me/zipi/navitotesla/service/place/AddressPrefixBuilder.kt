package me.zipi.navitotesla.service.place

object AddressPrefixBuilder {
    data class Prefix(
        val prefix: String,
        val isTruncated: Boolean,
    )

    /**
     * 1차 prefix 생성. 마지막 글자가 숫자이고 끝 토큰의 숫자가 2개 이상이면 마지막 1글자를 자른다.
     * 번지가 1자리거나 말미가 비숫자면 절단하지 않는다(1차==2차).
     */
    fun build(address: String): Prefix {
        val t = address.trim()
        if (t.isEmpty()) return Prefix("", false)
        val lastToken = t.substringAfterLast(' ')
        val digitCount = lastToken.count { it.isDigit() }
        return if (t.last().isDigit() && digitCount >= 2) {
            Prefix(t.dropLast(1), true)
        } else {
            Prefix(t, false)
        }
    }
}
