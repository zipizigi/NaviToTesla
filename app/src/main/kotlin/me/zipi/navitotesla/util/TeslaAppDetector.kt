package me.zipi.navitotesla.util

import android.content.Context
import android.content.pm.PackageManager

object TeslaAppDetector {
    const val TESLA_PACKAGE = "com.teslamotors.tesla"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun isInstalled(): Boolean {
        val context = appContext ?: return false
        return try {
            context.packageManager.getPackageInfo(TESLA_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            AnalysisUtil.warn("tesla app check failed: " + e.message)
            false
        }
    }
}
