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

/**
 * 4.48 이하는 알림 본문에 `목적지 : ~~~` 가 들어 있고, 4.49 부터는 사라졌다.
 * 두 경로를 함께 지원한다 — 본문에 목적지가 있으면 그대로 쓰고, 없으면 접근성으로 저장해 둔 값을 쓴다.
 */
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
        // 안전운전 진입에도 길안내 제목이 뜬다. 화면으로 확인한다.
        return driveModeProvider?.invoke() == NaviDriveMode.SAFE_DRIVE
    }

    override fun consumeCapturedDestination() = clearDestination()

    companion object {
        private const val LEGACY_DESTINATION_PREFIX = "목적지 : "

        /** 보험 ON 이면 제목이 바뀐다. 4.48 이하에서 보험 사용자가 전송되지 않던 원인. */
        private val GUIDANCE_TITLES = setOf("길안내 주행 중", "보험을 켜고 길안내 주행 중")

        /**
         * 실측 트리거 시점 캡처 나이는 정상 전송이 29초, 취소 후 오전송이 18초였다.
         * 나이로는 둘을 못 가른다. 짧게 잡으면 정상 전송이 먼저 막히므로 넉넉히 둔다.
         */
        private const val DESTINATION_TTL_MS = 60_000L

        @Volatile private var destination: String? = null

        @Volatile private var savedTime = 0L

        @Volatile private var driveModeProvider: (() -> NaviDriveMode)? = null

        /** 접근성 서비스가 붙어 있는 동안만 화면 판별이 가능하다. */
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

        /** 전송에 성공했거나 안내가 끝나면 버린다. 다음 트리거에 낡은 값이 실려 나가지 않도록. */
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
