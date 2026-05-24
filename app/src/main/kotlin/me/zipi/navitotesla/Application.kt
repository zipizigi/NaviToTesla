package me.zipi.navitotesla

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import com.google.android.libraries.places.api.Places
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.background.TokenWorker
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.service.place.PlacesAutocompleteClient
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppCheckUtil
import me.zipi.navitotesla.util.PreferencesUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import java.util.Locale

class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesUtil.initialize(this.applicationContext)
        AppDatabase.initialize(this.applicationContext)
        AppRepository.initialize(database)
        RemoteConfigUtil.initialize()
        AppCheckUtil.initialize()
        if (BuildConfig.BUILD_MODE == "playstore") {
            // RemoteConfigUtil.getString 이 Tasks.await 으로 main thread 를 차단할 수 있어 background 에서 수행.
            CoroutineScope(Dispatchers.IO).launch { initializePlacesSdk() }
        }
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

    private fun initializePlacesSdk() {
        val placesKey = RemoteConfigUtil.getString(RemoteConfigUtil.KEY_GOOGLE_PLACES_API_KEY)
        if (placesKey.isEmpty() || placesKey == "default") return
        try {
            Places.initializeWithNewPlacesApiEnabled(this.applicationContext, placesKey, Locale.KOREA)
            // Places SDK 응답 언어는 client context 의 locale 을 따른다. 디바이스 기본이 영어인 경우에도
            // 한국어 결과를 받기 위해 KOREA locale context 로 client 생성.
            val koreaConfig =
                Configuration(this.applicationContext.resources.configuration).apply {
                    setLocale(Locale.KOREA)
                }
            val koreaContext = this.applicationContext.createConfigurationContext(koreaConfig)
            PlacesAutocompleteClient.setClient(Places.createClient(koreaContext))
        } catch (e: Exception) {
            AnalysisUtil.log("Places SDK init failed: ${e.javaClass.simpleName}")
            AnalysisUtil.recordException(e)
        }
    }

    private val database: AppDatabase
        get() = AppDatabase.getInstance()
}
