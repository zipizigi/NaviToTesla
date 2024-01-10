package me.zipi.navitotesla.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import me.zipi.navitotesla.R

object RemoteConfigUtil {
    fun initialize() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings =
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds((20 * 3600).toLong())
                .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            Log.i(RemoteConfigUtil::class.java.name, "Remote config fetch ok")
        }
    }

    fun getString(key: String): String {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        return remoteConfig.getString(key)
    }

    fun getBoolean(key: String): Boolean {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        return remoteConfig.getBoolean(key)
    }
}
