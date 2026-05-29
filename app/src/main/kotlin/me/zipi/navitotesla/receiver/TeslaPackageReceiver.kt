package me.zipi.navitotesla.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.zipi.navitotesla.util.TeslaAppDetector

class TeslaPackageReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val changedPackage = intent.data?.schemeSpecificPart ?: return
        if (changedPackage != TeslaAppDetector.TESLA_PACKAGE) return
        TeslaAppDetector.invalidate()
    }
}
