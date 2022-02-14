package me.zipi.navitotesla.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;

public class ShareWorker extends Worker {
    public ShareWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void startShare(@NonNull Context context, String packageName, String notificationTitle, String notificationText) {
        AnalysisUtil.log("start share worker");
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
    public Result doWork() {
        Data inputData = getInputData();
        String packageName = inputData.getString("packageName");
        String notificationTitle = inputData.getString("notificationTitle");
        String notificationText = inputData.getString("notificationText");

        new NaviToTeslaService(getApplicationContext()).share(packageName, notificationTitle, notificationText);
        return Result.success();
    }
}