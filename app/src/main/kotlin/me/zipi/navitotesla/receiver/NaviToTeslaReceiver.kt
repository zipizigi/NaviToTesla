package me.zipi.navitotesla.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.R
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil

class NaviToTeslaReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val action = intent.action
            AnalysisUtil.log("receive NaviToTesla broadcast: $action")
            val applicationContext = context.applicationContext
            if (action.equals("navitotesla.ENABLE", ignoreCase = true)) {
                EnablerUtil.setAppEnabled(true)
                AnalysisUtil.makeToast(
                    applicationContext,
                    applicationContext.getString(R.string.enabledApp),
                )
            } else if (action.equals("navitotesla.DISABLE", ignoreCase = true)) {
                EnablerUtil.setAppEnabled(false)
                AnalysisUtil.makeToast(
                    applicationContext,
                    applicationContext.getString(R.string.disabledApp),
                )
            }
        }
    }
}
