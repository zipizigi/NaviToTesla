package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.exception.NotSupportedNaviException


object PoiFinderFactory {
    private const val tmapPackage = "com.skt.tmap.ku"
    private const val tmapSKPackage = "com.skt.skaf.l001mtm091"
    private const val kakaoPackage = "com.locnall.KimGiSa"
    private const val naverPackage = "com.nhn.android.nmap"
    fun isNaviSupport(packageName: String): Boolean {
        return (packageName.equals(tmapPackage, ignoreCase = true)
                || packageName.equals(tmapSKPackage, ignoreCase = true)
                || packageName.equals(kakaoPackage, ignoreCase = true)
                || packageName.equals(
            naverPackage,
            ignoreCase = true
        ))
    }

    @Throws(NotSupportedNaviException::class)
    fun getPoiFinder(packageName: String): PoiFinder {
        if (packageName.equals(tmapPackage, ignoreCase = true) || packageName.equals(
                tmapSKPackage,
                ignoreCase = true
            )
        ) {
            return TMapPoiFinder()
        } else if (packageName.equals(kakaoPackage, ignoreCase = true)) {
            return KakaoPoiFinder()
        } else if (packageName.equals(naverPackage, ignoreCase = true)) {
            return NaverPoiFinder()
        }
        throw NotSupportedNaviException(packageName)
    }

    val kakaoPoiFinder: PoiFinder
        get() = KakaoPoiFinder()
    val tMapPoiFinder: PoiFinder
        get() = TMapPoiFinder()
}