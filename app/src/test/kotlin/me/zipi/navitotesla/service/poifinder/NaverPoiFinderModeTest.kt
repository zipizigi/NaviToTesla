package me.zipi.navitotesla.service.poifinder

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NaverPoiFinderModeTest {
    private val finder = NaverPoiFinder()

    @Before
    @After
    fun clear() {
        NaverPoiFinder.clearDestination()
        NaverPoiFinder.setDriveModeProvider(null)
    }

    @Test
    fun `isIgnore returns false for guidance notification with capture`() {
        NaverPoiFinder.addDestination("서현역")
        assertFalse(finder.isIgnore("네이버지도", "내비게이션 - 안내 중"))
    }

    @Test
    fun `isIgnore returns true when screen shows safe drive`() {
        NaverPoiFinder.addDestination("서현역")
        NaverPoiFinder.setDriveModeProvider { NaviDriveMode.SAFE_DRIVE }
        assertTrue(finder.isIgnore("네이버지도", "내비게이션 - 안내 중"))
    }

    @Test
    fun `isIgnore returns false when screen shows guidance`() {
        NaverPoiFinder.addDestination("서현역")
        NaverPoiFinder.setDriveModeProvider { NaviDriveMode.GUIDANCE }
        assertFalse(finder.isIgnore("네이버지도", "내비게이션 - 안내 중"))
    }

    @Test
    fun `isIgnore returns true for other notification text`() {
        NaverPoiFinder.addDestination("서현역")
        assertTrue(finder.isIgnore("네이버지도", "다른 알림"))
    }

    @Test
    fun `placeholder text is not stored as destination`() {
        NaverPoiFinder.addDestination("Enter destination")
        assertTrue(NaverPoiFinder.isDestinationEmpty())
        NaverPoiFinder.addDestination("도착지 입력")
        assertTrue(NaverPoiFinder.isDestinationEmpty())
    }
}
