package me.zipi.navitotesla.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterUtilCompareVersionTest {
    @Test
    fun `1_98 is older than 1_100 (lexicographic minor)`() {
        assertTrue(AppUpdaterUtil.compareVersion("1.98", "1.100") < 0)
    }

    @Test
    fun `1_100 is newer than 1_98`() {
        assertTrue(AppUpdaterUtil.compareVersion("1.100", "1.98") > 0)
    }

    @Test
    fun `equal versions return 0`() {
        assertEquals(0, AppUpdaterUtil.compareVersion("1.91", "1.91"))
    }

    @Test
    fun `suffix like -debug or sha8 is ignored`() {
        assertEquals(0, AppUpdaterUtil.compareVersion("1.100-debug", "1.100"))
        assertEquals(0, AppUpdaterUtil.compareVersion("1.100.abc12345", "1.100"))
    }

    @Test
    fun `2_x is newer than 1_x`() {
        assertTrue(AppUpdaterUtil.compareVersion("1.999", "2.0") < 0)
    }

    @Test
    fun `2_00 is newer than 1_100 (major bump overrides large minor)`() {
        assertTrue(AppUpdaterUtil.compareVersion("2.00", "1.100") > 0)
        assertTrue(AppUpdaterUtil.compareVersion("1.100", "2.00") < 0)
    }

    @Test
    fun `2_100 is newer than 1_100 (same minor, major bump wins)`() {
        assertTrue(AppUpdaterUtil.compareVersion("2.100", "1.100") > 0)
        assertTrue(AppUpdaterUtil.compareVersion("1.100", "2.100") < 0)
    }

    @Test
    fun `different commit hash on same version is equal`() {
        assertEquals(0, AppUpdaterUtil.compareVersion("1.100.asdf", "1.100.efgh"))
    }

    @Test
    fun `commit hash suffix does not affect major-minor comparison`() {
        assertTrue(AppUpdaterUtil.compareVersion("1.98.abc12345", "1.100.def67890") < 0)
        assertTrue(AppUpdaterUtil.compareVersion("2.0.aaaaaaaa", "1.999.zzzzzzzz") > 0)
    }
}
