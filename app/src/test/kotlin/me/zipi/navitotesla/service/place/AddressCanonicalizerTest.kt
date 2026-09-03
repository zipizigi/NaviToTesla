package me.zipi.navitotesla.service.place

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressCanonicalizerTest {
    @Test
    fun `strips 대한민국 prefix`() {
        assertEquals(
            "서울 강남구 영동대로 513",
            AddressCanonicalizer.canonicalize("대한민국 서울특별시 강남구 영동대로 513"),
        )
    }

    @Test
    fun `collapses internal whitespace and trims`() {
        assertEquals(
            "서울 강남구 영동대로 513",
            AddressCanonicalizer.canonicalize("  서울특별시   강남구  영동대로 513 "),
        )
    }

    @Test
    fun `normalizes sido notation for clean kakao address`() {
        assertEquals("서울 중구 세종대로 110", AddressCanonicalizer.canonicalize("서울특별시 중구 세종대로 110"))
    }

    @Test
    fun `is identity for already canonical address`() {
        val canonical = "서울 중구 세종대로 110"
        assertEquals(canonical, AddressCanonicalizer.canonicalize(canonical))
    }

    @Test
    fun `does not strip 대한민국 when not a leading token`() {
        assertEquals("서울 대한민국로 1", AddressCanonicalizer.canonicalize("서울 대한민국로 1"))
    }

    @Test
    fun `대한민국 alone becomes empty`() {
        assertEquals("", AddressCanonicalizer.canonicalize("대한민국"))
    }

    @Test
    fun `places prediction fullText converges with app address`() {
        val fromPlaces = AddressCanonicalizer.canonicalize("대한민국 서울특별시 강남구 영동대로 513")
        val fromTmap = AddressCanonicalizer.canonicalize("서울 강남구 영동대로 513")
        assertEquals(fromTmap, fromPlaces)
    }
}
