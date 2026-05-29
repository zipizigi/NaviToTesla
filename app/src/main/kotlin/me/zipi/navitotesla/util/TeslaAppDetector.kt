package me.zipi.navitotesla.util

import android.content.Context
import me.zipi.navitotesla.BuildConfig

object TeslaAppDetector {
    const val TESLA_PACKAGE = "com.teslamotors.tesla"

    @Volatile
    private var cached: Boolean? = null

    fun isInstalled(context: Context): Boolean = cached ?: check(context).also { cached = it }

    fun invalidate() {
        cached = null
    }

    private fun check(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return runCatching {
            context.packageManager.getPackageInfo(TESLA_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
