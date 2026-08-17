package me.zipi.navitotesla.util

import android.content.Context
import android.content.pm.PackageManager

object TeslaAppDetector {
    const val TESLA_PACKAGE = "com.teslamotors.tesla"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cached: Boolean? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /** 설치돼 있다는 결과만 캐싱한다. 없다는 결과는 나중에 설치될 수 있으므로 매번 다시 본다. */
    fun isInstalled(): Boolean {
        cached?.let { return it }
        val context = appContext ?: return false
        return check(context).also { if (it) cached = true }
    }

    fun invalidate() {
        cached = null
    }

    private fun check(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(TESLA_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            AnalysisUtil.warn("tesla app check failed: " + e.message)
            false
        }
}
