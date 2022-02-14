package me.zipi.navitotesla.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.UUID;

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
import me.zipi.navitotesla.util.AnalysisUtil;

public class ShareWorker extends Worker {
    public ShareWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void startShare(@NonNull Context context, String packageName, String notificationTitle, String notificationText) {
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
        Notification notification = createNotification(getApplicationContext(), getId(), "목적지 전송중...");
        return CallbackToFutureAdapter.getFuture(f -> f.set(new ForegroundInfo(1, notification)));
    }

    @NonNull
    @Override
    public Result doWork() {
        AnalysisUtil.log("Start share worker");
        Data inputData = getInputData();
        String packageName = inputData.getString("packageName");
        String notificationTitle = inputData.getString("notificationTitle");
        String notificationText = inputData.getString("notificationText");

        new NaviToTeslaService(getApplicationContext()).share(packageName, notificationTitle, notificationText);
        return Result.success();
    }


    private Notification createNotification(Context context, UUID workRequestId, String notificationTitle) {

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    "location_share_channel", "Share notification",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, "location_share_channel")
                .setContentIntent(contentIntent)
                .setContentTitle(notificationTitle)
                .setTicker(notificationTitle)
                .setSmallIcon(R.drawable.ic_baseline_share_24)
                .setAutoCancel(true)
                .build();
    }
}