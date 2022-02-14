package me.zipi.navitotesla.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;

public class VersionCheckWorker extends Worker {

    public VersionCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }


    public static void startVersionCheck(@NonNull Context context) {
        AnalysisUtil.log("Register version check worker");
        WorkRequest workRequest = new OneTimeWorkRequest.Builder(VersionCheckWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        AnalysisUtil.log("Start version check worker");
        AppUpdaterUtil.notification(getApplicationContext());
        return Result.success();
    }
}
