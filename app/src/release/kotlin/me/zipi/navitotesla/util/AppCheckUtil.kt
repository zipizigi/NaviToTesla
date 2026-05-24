package me.zipi.navitotesla.util

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import me.zipi.navitotesla.BuildConfig

object AppCheckUtil {
    fun initialize() {
        if (BuildConfig.BUILD_MODE != "playstore") return
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
