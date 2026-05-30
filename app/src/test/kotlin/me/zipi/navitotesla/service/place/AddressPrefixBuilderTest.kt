package me.zipi.navitotesla.service.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressPrefixBuilderTest {
    @Test
    fun `truncates last digit of multi-digit number`() {
        val r = AddressPrefixBuilder.build("서울특별시 강남구 영동대로 1234")
        assertEquals("서울특별시 강남구 영동대로 123", r.prefix)
        assertTrue(r.isTruncated)
    }

    @Test
    fun `truncates trailing char of sub-number`() {
        val r = AddressPrefixBuilder.build("영동대로 1234-1")
        assertEquals("영동대로 1234-", r.prefix)
        assertTrue(r.isTruncated)
    }

    @Test
    fun `single-digit building number is not truncated`() {
        val r = AddressPrefixBuilder.build("영동대로 1")
        assertEquals("영동대로 1", r.prefix)
        assertFalse(r.isTruncated)
    }

    @Test
    fun `non-numeric tail is not truncated`() {
        val r = AddressPrefixBuilder.build("영동대로1길")
        assertEquals("영동대로1길", r.prefix)
        assertFalse(r.isTruncated)
    }

    @Test
    fun `trims before building prefix`() {
        val r = AddressPrefixBuilder.build("  영동대로 1234  ")
        assertEquals("영동대로 123", r.prefix)
        assertTrue(r.isTruncated)
    }

    @Test
    fun `empty stays empty`() {
        val r = AddressPrefixBuilder.build("   ")
        assertEquals("", r.prefix)
        assertFalse(r.isTruncated)
    }
}
