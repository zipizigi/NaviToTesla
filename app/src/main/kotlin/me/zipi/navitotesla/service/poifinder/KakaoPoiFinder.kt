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
            body.documents.forEach { place ->
                poiList.add(
                    Poi(
                        poiName = place.placeName,
                        roadAddress = place.roadAddressName.orEmpty(),
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

    override fun ignoreReason(
        notificationTitle: String,
        notificationText: String,
    ): IgnoreReason? {
        // 안전운전 모드는 알림 제목으로 확정 구분된다 — 화면 판정보다 우선.
        if (notificationTitle in SAFE_TITLES) return IgnoreReason.SAFE_TITLE
        if (notificationTitle !in GUIDANCE_TITLES) return IgnoreReason.TITLE_MISMATCH
        if (notificationText.contains(LEGACY_DESTINATION_PREFIX)) return null
        if (destination.isNullOrEmpty()) return IgnoreReason.NO_CAPTURE
        if (System.currentTimeMillis() - savedTime > DESTINATION_TTL_MS) return IgnoreReason.TTL_EXPIRED
        if (driveModeProvider?.invoke() == NaviDriveMode.SAFE_DRIVE) return IgnoreReason.SAFE_DRIVE
        return null
    }

    override fun consumeCapturedDestination() = clearDestination()

    companion object {
        private const val LEGACY_DESTINATION_PREFIX = "목적지 : "

        /** 보험 ON 이면 제목이 바뀐다. (noti_drive_title / noti_drive_insurance_title) */
        private val GUIDANCE_TITLES = setOf("길안내 주행 중", "보험을 켜고 길안내 주행 중")

        /** 목적지 없는 주행 모드. (noti_safety_drive_title / noti_safety_drive_insurance_title) */
        private val SAFE_TITLES = setOf("안전운전 주행 중", "보험을 켜고 안전운전 주행 중")

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
