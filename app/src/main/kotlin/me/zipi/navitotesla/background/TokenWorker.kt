package me.zipi.navitotesla.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.PreferencesUtil
import java.util.concurrent.TimeUnit

class TokenWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.i(TokenWorker::class.java.name, "Start background refresh token")
        AnalysisUtil.log("Start background refresh token")
        val service = NaviToTeslaService(applicationContext)
        val token = service.refreshToken()
        return if (token != null) {
            Result.success()
        } else {
            Result.failure()
        }
    }

    companion object {
        private const val workName = "refreshTokenWorker"
        fun startBackgroundWork(context: Context) {
            if (PreferencesUtil.loadToken(context) == null) {
                AnalysisUtil.log("Token is empty. add token refresh work ignore")
                return
            }
            AnalysisUtil.log("Add background refresh token")
            val workRequest: PeriodicWorkRequest =
                PeriodicWorkRequestBuilder<TokenWorker>(350, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun cancelBackgroundWork(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(workName)
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
            }
        }
    }
}