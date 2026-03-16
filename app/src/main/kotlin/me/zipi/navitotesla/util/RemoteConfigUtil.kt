package me.zipi.navitotesla.util

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import me.zipi.navitotesla.R

object RemoteConfigUtil {
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
            Log.i(RemoteConfigUtil::class.java.name, "Remote config fetch ok")
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
}
