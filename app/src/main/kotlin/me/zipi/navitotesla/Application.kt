package me.zipi.navitotesla

import android.app.Application
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.background.TokenWorker
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.PreferencesUtil
import me.zipi.navitotesla.util.RemoteConfigUtil

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesUtil.initialize(this.applicationContext)
        AppDatabase.initialize(this.applicationContext)
        AppRepository.initialize(database)
        RemoteConfigUtil.initialize()
        AnalysisUtil.initialize(this.applicationContext)
        AnalysisUtil.log(
            "App started: v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_MODE}, " +
                "sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL})",
        )
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        if (!BuildConfig.DEBUG) {
            CoroutineScope(Dispatchers.IO).launch { TokenWorker.startBackgroundWork(this@Application) }
        }
    }

    private val database: AppDatabase
        get() = AppDatabase.getInstance()
}
