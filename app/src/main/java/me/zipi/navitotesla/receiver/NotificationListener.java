package me.zipi.navitotesla.receiver;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import me.zipi.navitotesla.service.PoiFinderFactory;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AppUpdaterUtil;

public class NotificationListener extends NotificationListenerService {
    NaviToTeslaService naviToTeslaService;
    Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        naviToTeslaService = new NaviToTeslaService(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        naviToTeslaService = null;
        executor = null;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (PoiFinderFactory.isNaviSupport(sbn.getPackageName())) {
            Log.d(this.getClass().getName(), "onNotificationRemoved ~ " +
                    " packageName: " + sbn.getPackageName());
            executor.execute(() -> naviToTeslaService.notificationClear());

        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (PoiFinderFactory.isNaviSupport(sbn.getPackageName())) {
            Bundle extras = sbn.getNotification().extras;
            String title = extras.getString(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

            Log.d(this.getClass().getName(), "onNotificationPosted ~ " +
                    " packageName: " + sbn.getPackageName() +
                    " id: " + sbn.getId() +
                    " postTime: " + sbn.getPostTime() +
                    " title: " + title +
                    " text : " + text +
                    " subText: " + subText);
            executor.execute(() -> naviToTeslaService.share(sbn.getPackageName(), title, text.toString()));
            AppUpdaterUtil.notification(this);
        }
    }


}