package me.zipi.navitotesla.background;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class TokenWorker extends Worker {

    private static String workName = "refreshTokenWorker";

    public TokenWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void startBackgroundWork(@NonNull Context context) {
        if (PreferencesUtil.loadToken(context) == null) {
            AnalysisUtil.log("Token is empty. add work ignore");
            return;
        }
        AnalysisUtil.log("Add background refresh token");
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(TokenWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest);
    }

    public static void cancelBackgroundWork(@NonNull Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(workName);
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
        }
    }

    @NonNull
    @Override
    public Result doWork() {

        Log.i(TokenWorker.class.getName(), "Start background refresh token");
        AnalysisUtil.log("Start background refresh token");
        NaviToTeslaService service = new NaviToTeslaService(getApplicationContext());
        Token token = service.refreshToken();

        if (token != null) {
            return Result.success();
        } else {
            return Result.failure();
        }
    }
}
