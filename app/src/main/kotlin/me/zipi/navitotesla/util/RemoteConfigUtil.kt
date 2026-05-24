package me.zipi.navitotesla.util

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import me.zipi.navitotesla.R

object RemoteConfigUtil {
    const val KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED = "googlePlaceCheckLookupEnabled"
    const val KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED = "googlePlaceCheckUpdateEnabled"
    const val KEY_GOOGLE_PLACES_API_KEY = "googlePlacesApiKey"
    const val KEY_GOOGLE_PLACE_CHECK_TTL_DAYS = "googlePlaceCheckTtlDays"
    private const val FETCH_INTERVAL_SECONDS = 20 * 3600L
    private const val DEFAULT_VALUE = "default"

    fun initialize() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val needsImmediateFetch = remoteConfig.info.lastFetchStatus != FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS
        val configSettings =
            FirebaseRemoteConfigSettings
                .Builder()
                .setMinimumFetchIntervalInSeconds(if (needsImmediateFetch) 0L else FETCH_INTERVAL_SECONDS)
                .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            AnalysisUtil.log("Remote config fetch ok")
        }
    }

    fun getString(key: String): String {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val value = remoteConfig.getString(key)
        if (value != DEFAULT_VALUE) return value
        return try {
            Tasks.await(remoteConfig.fetchAndActivate())
            remoteConfig.getString(key)
        } catch (_: Exception) {
            value
        }
    }

    fun getBoolean(key: String): Boolean {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        return remoteConfig.getBoolean(key)
    }

    fun getLong(key: String): Long {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        return remoteConfig.getLong(key)
    }
}
