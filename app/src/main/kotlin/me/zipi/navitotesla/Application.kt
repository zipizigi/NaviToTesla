package me.zipi.navitotesla

import android.app.Application
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.zipi.navitotesla.background.TokenWorker
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.service.place.FirebaseAppCheckTokenProvider
import me.zipi.navitotesla.service.place.PlacesAutocompleteClient
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppCheckUtil
import me.zipi.navitotesla.util.PreferencesUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import java.util.Locale

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // 메인에서는 가벼운 init 만 (lazy/in-memory). PreferencesUtil 의 무거운 부분은 IO 로 위임.
        AppDatabase.initialize(this.applicationContext)
        AppRepository.initialize(database)
        AnalysisUtil.initialize(this.applicationContext)
        AnalysisUtil.log(
            "App started: v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_MODE}, " +
                "sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL})",
        )
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // 서로 독립인 init 들 — 병렬 실행
            listOf(
                launch { PreferencesUtil.initialize(applicationContext) },
                launch { RemoteConfigUtil.initialize() },
                launch { AppCheckUtil.initialize() },
            ).joinAll()
            // RemoteConfig 의존 → 순차
            if (BuildConfig.DEBUG || BuildConfig.BUILD_MODE == "playstore") {
                initializePlacesSdk()
            }
            if (!BuildConfig.DEBUG) {
                TokenWorker.startBackgroundWork(this@Application)
            }
        }
    }

    private fun initializePlacesSdk() {
        val placesKey = RemoteConfigUtil.getString(RemoteConfigUtil.KEY_GOOGLE_PLACES_API_KEY)
        if (placesKey.isEmpty() || placesKey == "default") return
        try {
            Places.initializeWithNewPlacesApiEnabled(this.applicationContext, placesKey, Locale.KOREA)
            Places.setPlacesAppCheckTokenProvider(FirebaseAppCheckTokenProvider())
            PlacesAutocompleteClient.setClient(Places.createClient(this.applicationContext))
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
        }
    }

    private val database: AppDatabase
        get() = AppDatabase.getInstance()
}
