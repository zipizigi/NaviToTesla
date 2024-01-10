package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.NotSupportedNaviException

object PoiFinderFactory {
    private const val TMAP_PACKAGE = "com.skt.tmap.ku"
    private const val TMAP_SK_PACKAGE = "com.skt.skaf.l001mtm091"
    private const val KAKAO_PACKAGE = "com.locnall.KimGiSa"
    private const val NAVER_PACKAGE = "com.nhn.android.nmap"

    fun isNaviSupport(packageName: String): Boolean {
        return listOf(TMAP_PACKAGE, TMAP_SK_PACKAGE, KAKAO_PACKAGE, NAVER_PACKAGE)
            .map { packageName.equals(it, ignoreCase = true) }.any { it }
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
