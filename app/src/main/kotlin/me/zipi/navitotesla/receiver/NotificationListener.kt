package me.zipi.navitotesla.receiver

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.VisibleForTesting
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.zipi.navitotesla.background.ShareWorker
import me.zipi.navitotesla.background.VersionCheckWorker
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import java.util.concurrent.ConcurrentHashMap

class NotificationListener : NotificationListenerService() {
    private lateinit var naviToTeslaService: NaviToTeslaService

    @VisibleForTesting
    internal var serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 같은 title 의 반복 update 로그 폭주 방지 — title 변경 시에만 로깅.
    private val lastTitleByPackage = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        naviToTeslaService = NaviToTeslaService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (PoiFinderFactory.isNaviSupport(sbn.packageName)) {
            AnalysisUtil.log("onNotificationRemoved ~ packageName: ${sbn.packageName}")
            lastTitleByPackage.remove(sbn.packageName)
            serviceScope.launch { naviToTeslaService.notificationClear() }
            val param = Bundle()
            param.putString("package", sbn.packageName)
            AnalysisUtil.logEvent("notification_removed", param)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (PoiFinderFactory.isNaviSupport(sbn.packageName)) {
            serviceScope.launch {
                val extras = sbn.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
                val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

                val titleChanged = lastTitleByPackage.put(sbn.packageName, title) != title
                if (titleChanged) {
                    AnalysisUtil.log(
                        "onNotificationPosted ~ packageName: ${sbn.packageName} " +
                            "id: ${sbn.id} postTime: ${sbn.postTime} title: $title " +
                            "text: $text subText: $subText bigText: $bigText",
                    )
                }

                val bundle = Bundle()
                bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "NotificationListener")
                bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "NotificationListener")
                AnalysisUtil.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
                AnalysisUtil.setCustomKey("packageName", sbn.packageName)
                ShareWorker.startShare(applicationContext, sbn.packageName, title, text)
                NaviToTeslaAccessibilityService.notifyIfAvailable(
                    applicationContext,
                    sbn.packageName,
                )
                RemoteConfigUtil.initialize()
                VersionCheckWorker.startVersionCheck(applicationContext)
                val param = Bundle()
                param.putString("package", sbn.packageName)
                AnalysisUtil.logEvent("notification_received", param)
            }
        }
    }
}
