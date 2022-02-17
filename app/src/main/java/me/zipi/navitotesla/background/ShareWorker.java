package me.zipi.navitotesla.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.service.PoiFinder;
import me.zipi.navitotesla.service.PoiFinderFactory;
import me.zipi.navitotesla.util.AnalysisUtil;

public class ShareWorker extends Worker {
    private final NaviToTeslaService naviToTeslaService;
    private final String channelId = "location_share_channel";

    public ShareWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        naviToTeslaService = new NaviToTeslaService(context);
    }

    public static void startShare(@NonNull Context context, @NonNull String packageName, String notificationTitle, String notificationText) {
        AnalysisUtil.log("Register share worker");
        WorkRequest workRequest = new OneTimeWorkRequest.Builder(ShareWorker.class)
                .setInputData(new Data.Builder()
                        .putString("packageName", packageName)
                        .putString("notificationTitle", notificationTitle)
                        .putString("notificationText", notificationText)
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        return CallbackToFutureAdapter.getFuture(f -> f.set(new ForegroundInfo(1, createNotification())));
    }

    @NonNull
    @Override
    public Result doWork() {
        AnalysisUtil.log("Start share worker");
        Data inputData = getInputData();
        String packageName = inputData.getString("packageName");
        String notificationTitle = inputData.getString("notificationTitle");
        String notificationText = inputData.getString("notificationText");

        naviToTeslaService.share(packageName, notificationTitle, notificationText);
        return Result.success();
    }


    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = null;
        try {
            channel = notificationManager.getNotificationChannel(channelId);
        } catch (Exception ignore) {
        }

        if (channel != null) {
            return;
        }

        // create channel
        AnalysisUtil.log("create notification channel - " + channelId);

        NotificationChannel mChannel = new NotificationChannel(channelId, "Share notification", NotificationManager.IMPORTANCE_LOW);
        mChannel.setSound(null, null);
        mChannel.setShowBadge(false);
        mChannel.setDescription("차량에 위치를 공유할 때 알림이 나타납니다.");
        mChannel.setVibrationPattern(new long[]{0L});
        notificationManager.createNotificationChannel(mChannel);

    }

    private Notification createNotification() {
        Context context = getApplicationContext();

        String packageName = getInputData().getString("packageName");
        String notificationText = getInputData().getString("notificationText");

        createNotificationChannel();


        String poiName = "";
        String address = "";
        if (packageName != null) {
            PoiFinder poiFinder = PoiFinderFactory.getPoiFinder(packageName);
            address = poiFinder.parseDestination(notificationText);
            if (!naviToTeslaService.isAddress(address)) {
                poiName = address;
            }
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, channelId)
                .setContentIntent(contentIntent)
                .setContentText(context.getString(R.string.sendingDestination) + "\n" + address)
                .setTicker(context.getString(R.string.sendingDestination) + "\n" + poiName)
                .setSmallIcon(R.drawable.ic_baseline_share_24)
                .setAutoCancel(true)
                .setVibrate(new long[]{0L})
                .setSound(null)
                .build();
    }
}