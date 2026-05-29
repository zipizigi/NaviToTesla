package me.zipi.navitotesla.util

import android.content.Context

object TeslaAppDetector {
    const val TESLA_PACKAGE = "com.teslamotors.tesla"

    @Volatile
    private var cached: Boolean? = null

    fun isInstalled(context: Context): Boolean = cached ?: check(context).also { cached = it }

    fun invalidate() {
        cached = null
    }

    private fun check(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(TESLA_PACKAGE, 0)
            true
        }.getOrDefault(false)
}
