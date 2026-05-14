package me.zipi.navitotesla.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RelaunchNotifier

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(context)) return
        AnalysisUtil.log("PackageReplaced — relaunch notification")
        RelaunchNotifier.show(context)
    }
}
