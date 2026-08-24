package me.zipi.navitotesla.service.poifinder

import android.content.pm.PackageManager
import me.zipi.navitotesla.exception.NotSupportedNaviException

object PoiFinderFactory {
    const val TMAP_PACKAGE = "com.skt.tmap.ku"
    const val TMAP_SK_PACKAGE = "com.skt.skaf.l001mtm091"
    const val KAKAO_PACKAGE = "com.locnall.KimGiSa"
    const val NAVER_PACKAGE = "com.nhn.android.nmap"

    fun isNaverMap(packageName: String): Boolean = packageName.equals(NAVER_PACKAGE, ignoreCase = true)

    fun isKakaoNavi(packageName: String): Boolean = packageName.equals(KAKAO_PACKAGE, ignoreCase = true)

    fun isAccessibilityRequired(
        packageName: String,
        notificationText: String,
    ): Boolean =
        isNaverMap(packageName) ||
            (isKakaoNavi(packageName) && !KakaoPoiFinder.hasLegacyDestination(notificationText))

    /** 접근성 서비스 없이는 목적지를 얻을 수 없는 내비. TMAP 은 알림으로 취득하므로 제외한다. */
    private val ACCESSIBILITY_PACKAGES = listOf(KAKAO_PACKAGE, NAVER_PACKAGE)

    fun isAccessibilityNaviInstalled(packageManager: PackageManager): Boolean =
        ACCESSIBILITY_PACKAGES.any { isInstalled(packageManager, it) }

    private fun isInstalled(
        packageManager: PackageManager,
        packageName: String,
    ): Boolean =
        try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun isNaviSupport(packageName: String): Boolean =
        listOf(TMAP_PACKAGE, TMAP_SK_PACKAGE, KAKAO_PACKAGE, NAVER_PACKAGE)
            .any { packageName.equals(it, ignoreCase = true) }

    fun clearAllCapturedDestinations() {
        listOf(TMapPoiFinder(), KakaoPoiFinder(), NaverPoiFinder())
            .forEach { it.consumeCapturedDestination() }
    }

    @Throws(NotSupportedNaviException::class)
    fun getPoiFinder(packageName: String): PoiFinder =
        when {
            packageName.equals(TMAP_PACKAGE, ignoreCase = true) -> TMapPoiFinder()
            packageName.equals(TMAP_SK_PACKAGE, ignoreCase = true) -> TMapPoiFinder()
            packageName.equals(KAKAO_PACKAGE, ignoreCase = true) -> KakaoPoiFinder()
            packageName.equals(NAVER_PACKAGE, ignoreCase = true) -> NaverPoiFinder()
            else -> throw NotSupportedNaviException(packageName)
        }
}
