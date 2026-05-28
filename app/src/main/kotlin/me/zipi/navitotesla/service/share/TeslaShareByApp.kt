package me.zipi.navitotesla.service.share

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.net.URLEncoder
import java.util.Locale
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.util.AnalysisUtil

// locale.language == "en" — only English; other locales (jp/zh/etc) leave raw text as-is
// !payload.viaUrl — if SendPlanner already produced a URL (NOT_SEARCHABLE or NAME mode), don't double-wrap
// payload.mode != SendMode.GPS — coordinates are locale-neutral and Tesla parses them fine; no wrap
internal fun resolveShareIntentText(
    payload: SendPayload,
    locale: Locale,
): String =
    if (locale.language == "en" && !payload.viaUrl && payload.mode != SendMode.GPS) {
        "https://maps.google.com/maps?q=" + URLEncoder.encode(payload.sendText, "UTF-8")
    } else {
        payload.sendText
    }

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
        val sendText = resolveShareIntentText(payload, Locale.getDefault())
        intent.putExtra("android.intent.extra.TEXT", sendText)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (BuildConfig.DEBUG) {
            AnalysisUtil.makeToast(context, "[DEBUG] 목적지 전송 By App Skip\n${payload.displayText}")
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
