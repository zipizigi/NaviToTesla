package me.zipi.navitotesla.util;


import android.util.Log;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.zipi.navitotesla.R;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RemoteConfigUtil {
    public static void initialize() {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(20 * 3600)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);

        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> Log.i(RemoteConfigUtil.class.getName(), "Remote config fetch ok"));
    }

    @Deprecated
    public static String getConfig(String key) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        return remoteConfig.getString(key);
    }

    public static String getString(String key) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        return remoteConfig.getString(key);
    }

    public static Boolean getBoolean(String key) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        return remoteConfig.getBoolean(key);
    }

}
