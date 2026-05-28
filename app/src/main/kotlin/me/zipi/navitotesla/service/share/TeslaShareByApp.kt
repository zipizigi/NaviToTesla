package me.zipi.navitotesla.service.share

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.util.AnalysisUtil

class TeslaShareByApp(
    context: Context,
    private val packageName: String?,
) : TeslaShareBase(context),
    TeslaShare {
    override suspend fun share(payload: SendPayload) {
        AnalysisUtil.log("share using tesla app share")
        val intent = Intent()
        intent.action = "android.intent.action.SEND"
        intent.component = ComponentName("com.teslamotors.tesla", "com.tesla.share.ShareActivity")
        intent.type = "text/plain"
        intent.putExtra("android.intent.extra.TEXT", payload.sendText)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (BuildConfig.DEBUG) {
            AnalysisUtil.makeToast(
                context,
                "[DEBUG] 목적지 전송 By App Skip\n" +
                    "display=${payload.displayText}\n" +
                    "send=${payload.sendText}",
            )
            return
        }
        try {
            context.startActivity(intent)
            AnalysisUtil.logEvent("share_by_app_success", eventParam(packageName, payload))
        } catch (e: ActivityNotFoundException) {
            AnalysisUtil.log("Tesla app is not installed")
            AnalysisUtil.logEvent("share_by_app_fail", eventParam(packageName, payload))
        }
    }
}
