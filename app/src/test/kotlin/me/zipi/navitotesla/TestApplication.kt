package me.zipi.navitotesla

import android.app.Application

/**
 * Robolectric 가 운영용 [me.zipi.navitotesla.Application] 을 띄우려 하면 EncryptedSharedPreferences /
 * Room / Firebase 초기화가 JVM 환경에서 줄줄이 깨진다. 테스트에서는 아무것도 안 하는 빈 Application 으로 대체.
 */
class TestApplication : Application()
