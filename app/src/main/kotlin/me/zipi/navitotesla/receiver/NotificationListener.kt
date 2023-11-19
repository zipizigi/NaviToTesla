package me.zipi.navitotesla.receiver

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import me.zipi.navitotesla.AppExecutors
import me.zipi.navitotesla.background.ShareWorker
import me.zipi.navitotesla.background.VersionCheckWorker
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil

class NotificationListener : NotificationListenerService() {
    private var naviToTeslaService: NaviToTeslaService? = null
    override fun onCreate() {
        super.onCreate()
        naviToTeslaService = NaviToTeslaService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        naviToTeslaService = null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        if (PoiFinderFactory.isNaviSupport(sbn.packageName)) {
            Log.i(
                this.javaClass.name, "onNotificationRemoved ~ " +
                        " packageName: " + sbn.packageName
            )
            AppExecutors.execute { naviToTeslaService!!.notificationClear() }
            val param = Bundle()
            param.putString("package", sbn.packageName)
            AnalysisUtil.logEvent("notification_removed", param)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (PoiFinderFactory.isNaviSupport(sbn.packageName) && sbn.postTime - lastNotificationPosted > 2500) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "NotificationListener")
            bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "NotificationListener")
            AnalysisUtil.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            AnalysisUtil.setCustomKey("packageName", sbn.packageName)
            lastNotificationPosted = sbn.postTime
            val extras = sbn.notification.extras
            val title =  extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

            Log.i(
                this.javaClass.name, "onNotificationPosted ~ " +
                        " packageName: " + sbn.packageName +
                        " id: " + sbn.id +
                        " postTime: " + sbn.postTime +
                        " title: " + title +
                        " text : " + text +
                        " subText: " + subText
            )
            ShareWorker.startShare(applicationContext, sbn.packageName, title, text)
            NaviToTeslaAccessibilityService.notifyIfAvailable(
                applicationContext,
                sbn.packageName
            )
            AppExecutors.execute(RemoteConfigUtil::initialize)
            VersionCheckWorker.startVersionCheck(applicationContext)
            val param = Bundle()
            param.putString("package", sbn.packageName)
            AnalysisUtil.logEvent("notification_received", param)
        }
    }

    companion object {
        private var lastNotificationPosted = System.currentTimeMillis()
    }
}