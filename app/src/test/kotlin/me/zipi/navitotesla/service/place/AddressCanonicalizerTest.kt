package me.zipi.navitotesla.service.place

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressCanonicalizerTest {
    @Test
    fun `strips 대한민국 prefix`() {
        assertEquals(
            "서울특별시 강남구 영동대로 513",
            AddressCanonicalizer.canonicalize("대한민국 서울특별시 강남구 영동대로 513"),
        )
    }

    @Test
    fun `collapses internal whitespace and trims`() {
        assertEquals(
            "서울특별시 강남구 영동대로 513",
            AddressCanonicalizer.canonicalize("  서울특별시   강남구  영동대로 513 "),
        )
    }

    @Test
    fun `is identity for clean kakao address`() {
        val kakao = "서울특별시 중구 세종대로 110"
        assertEquals(kakao, AddressCanonicalizer.canonicalize(kakao))
    }

    @Test
    fun `does not strip 대한민국 when not a leading token`() {
        assertEquals("서울 대한민국로 1", AddressCanonicalizer.canonicalize("서울 대한민국로 1"))
    }
}
