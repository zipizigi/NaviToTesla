package me.zipi.navitotesla.receiver;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.lang3.StringUtils;

import me.zipi.navitotesla.AppExecutors;
import me.zipi.navitotesla.background.ShareWorker;
import me.zipi.navitotesla.background.VersionCheckWorker;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.RemoteConfigUtil;

public class NotificationListener extends NotificationListenerService {
    private static Long lastNotificationPosted = System.currentTimeMillis();
    NaviToTeslaService naviToTeslaService;

    @Override
    public void onCreate() {
        super.onCreate();
        naviToTeslaService = new NaviToTeslaService(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        naviToTeslaService = null;
    }


    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (PoiFinderFactory.isNaviSupport(sbn.getPackageName())) {
            Log.i(this.getClass().getName(), "onNotificationRemoved ~ " +
                    " packageName: " + sbn.getPackageName());
            AppExecutors.execute(() -> naviToTeslaService.notificationClear());
            Bundle param = new Bundle();
            param.putString("package", sbn.getPackageName());
            AnalysisUtil.logEvent("notification_removed", param);
        }
    }


    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (PoiFinderFactory.isNaviSupport(sbn.getPackageName()) && sbn.getPostTime() - lastNotificationPosted > 2500) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "NotificationListener");
            bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "NotificationListener");
            AnalysisUtil.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);

            AnalysisUtil.setCustomKey("packageName", sbn.getPackageName());
            lastNotificationPosted = sbn.getPostTime();
            Bundle extras = sbn.getNotification().extras;
            String title = StringUtils.defaultString(extras.getString(Notification.EXTRA_TITLE), "");
            String text = StringUtils.defaultString(extras.getString(Notification.EXTRA_TEXT), "");
            String subText = StringUtils.defaultString(extras.getString(Notification.EXTRA_SUB_TEXT), "");

            Log.i(this.getClass().getName(), "onNotificationPosted ~ " +
                    " packageName: " + sbn.getPackageName() +
                    " id: " + sbn.getId() +
                    " postTime: " + sbn.getPostTime() +
                    " title: " + title +
                    " text : " + text +
                    " subText: " + subText);
            ShareWorker.startShare(getApplicationContext(), sbn.getPackageName(), title, text);
            AppExecutors.execute(RemoteConfigUtil::initialize);
            VersionCheckWorker.startVersionCheck(getApplicationContext());
            Bundle param = new Bundle();
            param.putString("package", sbn.getPackageName());
            AnalysisUtil.logEvent("notification_received", param);
        }
    }


}