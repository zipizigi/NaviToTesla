package me.zipi.navitotesla

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
            TokenWorker.startBackgroundWork(this)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
        }
    }

    private val database: AppDatabase
        get() = AppDatabase.getInstance()
}