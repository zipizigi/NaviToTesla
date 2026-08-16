package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.api.KakaoMapApi
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.HttpRetryInterceptor
import me.zipi.navitotesla.util.RemoteConfigUtil
import me.zipi.navitotesla.util.ResponseCloser
import me.zipi.navitotesla.util.TextNormalizer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 알림 본문에 `목적지 : ~~~` 가 있으면 그대로, 없으면 접근성으로 저장해 둔 값을 쓴다. */
class KakaoPoiFinder : PoiFinder {
    override fun parseDestination(notificationText: String): String {
        if (notificationText.contains(LEGACY_DESTINATION_PREFIX)) {
            return TextNormalizer.normalize(notificationText.replace(LEGACY_DESTINATION_PREFIX, ""))
        }
        return destination ?: ""
    }

    @Throws(IOException::class)
    override suspend fun listPoiAddress(poiName: String): List<Poi> {
        val poiList = mutableListOf<Poi>()
        val response = kakaoMapApi.search(poiName)
        if (!response.isSuccessful || response.body() == null) {
            AnalysisUtil.warn("Kakao api error: " + response.errorBody()?.string().orEmpty())
        }
        response.body()?.let { body ->
            val withLocalName = RemoteConfigUtil.getBoolean("withLocalName") // 법정동 포함 여부
            body.documents.forEach { place ->
                poiList.add(
                    Poi(
                        poiName = place.placeName,
                        roadAddress = place.getRoadAddressName(withLocalName),
                        address = place.addressName,
                        longitude = place.longitude,
                        latitude = place.latitude,
                    ),
                )
            }
        }
        ResponseCloser.closeAll(response)
        return poiList
    }

    override fun isIgnore(
        notificationTitle: String,
        notificationText: String,
    ): Boolean {
        if (notificationTitle !in GUIDANCE_TITLES) return true
        if (notificationText.contains(LEGACY_DESTINATION_PREFIX)) return false
        if (destination.isNullOrEmpty()) return true
        if (System.currentTimeMillis() - savedTime > DESTINATION_TTL_MS) return true
        return driveModeProvider?.invoke() == NaviDriveMode.SAFE_DRIVE
    }

    override fun consumeCapturedDestination() = clearDestination()

    companion object {
        private const val LEGACY_DESTINATION_PREFIX = "목적지 : "

        /** 보험 ON 이면 제목이 바뀐다. */
        private val GUIDANCE_TITLES = setOf("길안내 주행 중", "보험을 켜고 길안내 주행 중")

        private const val DESTINATION_TTL_MS = 60_000L

        @Volatile private var destination: String? = null

        @Volatile private var savedTime = 0L

        @Volatile private var driveModeProvider: (() -> NaviDriveMode)? = null

        fun setDriveModeProvider(provider: (() -> NaviDriveMode)?) {
            driveModeProvider = provider
        }

        fun hasLegacyDestination(notificationText: String): Boolean = notificationText.contains(LEGACY_DESTINATION_PREFIX)

        fun isDestinationEmpty(): Boolean = destination.isNullOrEmpty()

        fun addDestination(dest: String) {
            val cleaned = TextNormalizer.normalize(dest)
            if (cleaned.isEmpty()) return
            savedTime = System.currentTimeMillis()
            if (cleaned == destination) return
            destination = cleaned
        }

        fun clearDestination() {
            destination = null
            savedTime = 0L
        }

        private val kakaoMapApi =
            Retrofit
                .Builder()
                .baseUrl("https://dapi.kakao.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient
                        .Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(
                            Interceptor { chain: Interceptor.Chain ->
                                val request =
                                    chain
                                        .request()
                                        .newBuilder()
                                        .addHeader(
                                            "Authorization",
                                            "KakaoAK " + RemoteConfigUtil.getString("kakaoApiKey"),
                                        ).build()
                                chain.proceed(request)
                            },
                        ).addInterceptor(HttpRetryInterceptor(10))
                        .build(),
                ).build()
                .create(KakaoMapApi::class.java)
    }
}
