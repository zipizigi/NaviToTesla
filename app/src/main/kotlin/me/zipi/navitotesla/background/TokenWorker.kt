package me.zipi.navitotesla.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.PreferencesUtil
import java.util.concurrent.TimeUnit

class TokenWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
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
        private const val WORKER_NAME = "refreshTokenWorker"

        fun startBackgroundWork(context: Context) {
            CoroutineScope(Dispatchers.Main).launch {
                if (PreferencesUtil.loadToken() == null) {
                    AnalysisUtil.log("Token is empty. add token refresh work ignore")
                    return@launch
                }
                AnalysisUtil.log("Add background refresh token")
                val workRequest: PeriodicWorkRequest =
                    PeriodicWorkRequestBuilder<TokenWorker>(350, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORKER_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest,
                )
            }
        }

        fun cancelBackgroundWork(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORKER_NAME)
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
            }
        }
    }
}
