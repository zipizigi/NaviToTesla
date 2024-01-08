package me.zipi.navitotesla.service.share

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import me.zipi.navitotesla.util.AnalysisUtil

class TeslaShareByApp(context: Context) : TeslaShareBase(context), TeslaShare {
    override suspend fun share(address: String) {
        AnalysisUtil.log("share using tesla app share")
        val intent = Intent()
        intent.action = "android.intent.action.SEND"
        intent.component = ComponentName("com.teslamotors.tesla", "com.tesla.share.ShareActivity")
        intent.type = "text/plain"
        intent.putExtra("android.intent.extra.TEXT", address)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            AnalysisUtil.logEvent("share_by_app_success", Bundle())
        } catch (e: ActivityNotFoundException) {
            AnalysisUtil.log("Tesla app is not installed")
            AnalysisUtil.logEvent("share_by_app_fail", Bundle())
        }
    }
}
