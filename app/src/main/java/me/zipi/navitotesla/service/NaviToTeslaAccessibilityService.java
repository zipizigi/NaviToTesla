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

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NotificationCompat;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.service.poifinder.NaverPoiFinder;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class NaviToTeslaAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED && event.getEventType() != AccessibilityEvent.TYPE_VIEW_SELECTED) {
                return;
            }

            if (event.getPackageName().equals("com.nhn.android.nmap")) {
                // naver navi, 목적지 저장
                List<String> goalList = new ArrayList<>();
                // portrait
                List<AccessibilityNodeInfo> portrait = getRootInActiveWindow().findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/search_goal");
                // landscape
                List<AccessibilityNodeInfo> landscape = getRootInActiveWindow().findAccessibilityNodeInfosByViewId("com.nhn.android.nmap:id/search_goal");
                goalList.addAll(parseNaverNaviDestination(portrait));
                goalList.addAll(parseNaverNaviDestination(landscape));

                if (goalList.size() > 0) {
                    NaverPoiFinder.addDestination(goalList.get(0));
                }
            }
        } catch (Exception e) {
            AnalysisUtil.warn("accessibility error: " + e.getMessage());
            AnalysisUtil.recordException(e);
        }

    }

    @Override
    public void onInterrupt() {

    }

    private List<String> parseNaverNaviDestination(List<AccessibilityNodeInfo> goalList) {
        List<String> result = new ArrayList<>();
        if (goalList != null) {
            for (AccessibilityNodeInfo node : goalList) {
                if (node != null && node.getText() != null && node.getText().toString().length() > 0) {
                    result.add(node.getText().toString());
                }
            }
        }
        return result;
    }


    private static String lastNotifyAppVersion = null;

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
        if (context == null) {
            return;
        }
        if (isAccessibilityServiceEnabled(context)) {
            return;
        }

        // AppUpdaterUtil.getCurrentVersion(this.getContext()
        String currentVersion = AppUpdaterUtil.getCurrentVersion(context);
        if (lastNotifyAppVersion == null) {
            lastNotifyAppVersion = PreferencesUtil.getString(context, "lastNotifyAppVersionForAccessibility");
        }
        if (lastNotifyAppVersion != null && lastNotifyAppVersion.equals(currentVersion)) {
            return;
        }

        lastNotifyAppVersion = currentVersion;
        PreferencesUtil.put(context, "lastNotifyAppVersionForAccessibility", currentVersion);

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
        if (context == null) {
            return false;
        }
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
