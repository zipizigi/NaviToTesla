package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.NotSupportedNaviException

object PoiFinderFactory {
    const val TMAP_PACKAGE = "com.skt.tmap.ku"
    const val TMAP_SK_PACKAGE = "com.skt.skaf.l001mtm091"
    const val KAKAO_PACKAGE = "com.locnall.KimGiSa"
    const val NAVER_PACKAGE = "com.nhn.android.nmap"

    fun isNaverMap(packageName: String): Boolean = packageName.equals(NAVER_PACKAGE, ignoreCase = true)

    fun isKakaoNavi(packageName: String): Boolean = packageName.equals(KAKAO_PACKAGE, ignoreCase = true)

    /** 카카오는 알림에 목적지가 들어 있는 구버전이면 접근성이 필요 없다. */
    fun isAccessibilityRequired(
        packageName: String,
        notificationText: String,
    ): Boolean =
        isNaverMap(packageName) ||
            (isKakaoNavi(packageName) && !KakaoPoiFinder.hasLegacyDestination(notificationText))

    fun isNaviSupport(packageName: String): Boolean =
        listOf(TMAP_PACKAGE, TMAP_SK_PACKAGE, KAKAO_PACKAGE, NAVER_PACKAGE)
            .any { packageName.equals(it, ignoreCase = true) }

    /** 트립이 끝났다. 접근성으로 모아 둔 목적지를 전부 버린다. */
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
