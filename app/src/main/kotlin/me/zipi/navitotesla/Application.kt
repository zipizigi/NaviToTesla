package me.zipi.navitotesla

import android.app.Application
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
        if (!BuildConfig.DEBUG) {
            CoroutineScope(Dispatchers.IO).launch { TokenWorker.startBackgroundWork(this@Application) }
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = false
        }
    }

    private val database: AppDatabase
        get() = AppDatabase.getInstance()
}
