package me.zipi.navitotesla.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.zipi.navitotesla.background.ShareWorker

/**
 * ADB 로 알림을 시뮬레이션하는 debug 전용 receiver. release 빌드에는 포함되지 않음.
 *
 *   adb shell "am broadcast -a me.zipi.navitotesla.DEBUG_TRIGGER \
 *       --es pkg 'com.skt.tmap.ku' --es title '경로주행' --es text '내 위치 > 강남역' \
 *       -p me.zipi.navitotesla.ns.debug"
 */
class DebugTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pkg = intent.getStringExtra("pkg")
        val title = intent.getStringExtra("title")
        val text = intent.getStringExtra("text")
        if (pkg.isNullOrEmpty() || text.isNullOrEmpty()) return
        ShareWorker.startShare(context.applicationContext, pkg, title, text)
    }
}
