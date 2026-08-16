package me.zipi.navitotesla.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `converts no break space to plain space`() {
        assertEquals(
            "\uC624\uB9AC\uC5ED \uC218\uC778\uBD84\uB2F9\uC120",
            TextNormalizer.normalize("\uC624\uB9AC\uC5ED\u00A0\uC218\uC778\uBD84\uB2F9\uC120"),
        )
    }

    @Test
    fun `converts non breaking hyphen to plain hyphen`() {
        assertEquals("40-1", TextNormalizer.normalize("40\u20111"))
    }

    @Test
    fun `converts every dash variant`() {
        assertEquals("a-b", TextNormalizer.normalize("a\u2013b"))
        assertEquals("a-b", TextNormalizer.normalize("a\u2212b"))
        assertEquals("a-b", TextNormalizer.normalize("a\uFF0Db"))
    }

    @Test
    fun `strips zero width characters`() {
        assertEquals("ab", TextNormalizer.normalize("a\u200Bb"))
        assertEquals("ab", TextNormalizer.normalize("a\uFEFFb"))
    }

    @Test
    fun `collapses repeated whitespace and trims`() {
        assertEquals("a b", TextNormalizer.normalize("  a\u00A0\u00A0 b  "))
    }

    @Test
    fun `handles null and empty`() {
        assertEquals("", TextNormalizer.normalize(null))
        assertEquals("", TextNormalizer.normalize(""))
    }

    @Test
    fun `leaves plain text untouched`() {
        assertEquals("\uC11C\uD604\uC5ED", TextNormalizer.normalize("\uC11C\uD604\uC5ED"))
    }
}
