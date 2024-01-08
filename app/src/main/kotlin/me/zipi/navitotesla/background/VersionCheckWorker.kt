package me.zipi.navitotesla.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import java.util.concurrent.TimeUnit

class VersionCheckWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        CoroutineScope(Dispatchers.Default).launch {
            AnalysisUtil.log("Start version check worker")
            AppUpdaterUtil.notification(applicationContext)
        }
        return Result.success()
    }

    companion object {
        @Suppress("KotlinConstantConditions")
        fun startVersionCheck(context: Context) {
            if (BuildConfig.BUILD_MODE == "playstore") {
                return
            }
            AnalysisUtil.log("Register version check worker")
            val workRequest: WorkRequest = OneTimeWorkRequestBuilder<VersionCheckWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ).setInitialDelay(1, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
