package me.zipi.navitotesla.util

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import me.zipi.navitotesla.BuildConfig

object AppCheckUtil {
    fun initialize() {
        if (BuildConfig.BUILD_MODE != "playstore") return
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
