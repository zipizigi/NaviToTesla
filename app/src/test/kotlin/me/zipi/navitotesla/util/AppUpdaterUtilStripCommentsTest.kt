package me.zipi.navitotesla.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdaterUtilStripCommentsTest {
    @Test
    fun `returns empty for null`() {
        assertEquals("", AppUpdaterUtil.extractDescription(null))
    }

    @Test
    fun `returns trimmed input when no comments and no auto-notes marker`() {
        assertEquals("hello world", AppUpdaterUtil.extractDescription("  hello world  "))
    }

    @Test
    fun `strips single-line HTML comment`() {
        assertEquals("after", AppUpdaterUtil.extractDescription("<!-- hidden -->after"))
    }

    @Test
    fun `strips multi-line HTML comment wrapping auto notes`() {
        val input =
            """
            description

            <!--
            ## What's Changed
            * feat: foo in #1
            * fix: bar in #2
            -->
            """.trimIndent()
        assertEquals("description", AppUpdaterUtil.extractDescription(input))
    }

    @Test
    fun `truncates at auto-notes marker for legacy releases without comment wrap`() {
        val input =
            """
            v1.95 changes
            - fix: foo

            ## What's Changed
            * chore(deps): bump in #1
            **Full Changelog**: ...
            """.trimIndent()
        assertEquals(
            "v1.95 changes\n- fix: foo",
            AppUpdaterUtil.extractDescription(input),
        )
    }

    @Test
    fun `strips multiple HTML comments`() {
        val input = "<!-- a -->visible<!-- b -->"
        assertEquals("visible", AppUpdaterUtil.extractDescription(input))
    }
}
