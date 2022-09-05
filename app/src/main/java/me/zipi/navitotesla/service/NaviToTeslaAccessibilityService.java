package me.zipi.navitotesla.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

import androidx.core.app.NotificationCompat;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.service.poifinder.NaverPoiFinder;

public class NaviToTeslaAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SELECTED) {
            return;
        }

        if (event.getPackageName().equals("com.nhn.android.nmap")) {
            // naver navi, 목적지 저장
            List<AccessibilityNodeInfo> goalList = getRootInActiveWindow().findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/search_goal");
            if (goalList.size() > 0) {
                NaverPoiFinder.addDestination(goalList.get(0).getText().toString());
            }
        }

    }

    @Override
    public void onInterrupt() {

    }


    /**
     * 내비게이션이 있고, 접근성이 필요하다면, 노티알림
     *
     * @param context     context
     * @param packageName packageName
     */
    public static void notifyIfAvailable(Context context, String packageName) {
        // possible package
        if (!packageName.equalsIgnoreCase("com.nhn.android.nmap")) {
            return;
        }
        if (isAccessibilityServiceEnabled(context)) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    "notification_channel", "Notification",
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(mChannel);
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.putExtra("noti_action", "requireAccessibility");

        PendingIntent contentIntent = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, "notification_channel")
                .setContentIntent(contentIntent)
                .setContentTitle(context.getString(R.string.requireAccessibility))
                .setContentText(context.getString(R.string.guideRequireAccessibility))
                .setSmallIcon(R.drawable.ic_baseline_accessibility_new_24)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(2, notification);
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo enabledService : enabledServices) {
            ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
            if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(NaviToTeslaAccessibilityService.class.getName()))
                return true;
        }

        return false;
    }
}
