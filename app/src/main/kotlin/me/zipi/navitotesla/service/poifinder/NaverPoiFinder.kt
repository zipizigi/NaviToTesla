package me.zipi.navitotesla.service.poifinder

import me.zipi.navitotesla.api.NaverFusionSearchApi
import me.zipi.navitotesla.api.NaverMapApi
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
import kotlin.random.Random

/**
 * naver navi poi finder
 * 화면이 변경되면, 접근성 도구로 목적지를 내부 변수(destination)에 저장해둔다. 이후 안내가 시작되면 해당 목적지를 전송한다.
 */
class NaverPoiFinder : PoiFinder {
    override fun parseDestination(notificationText: String): String {
        // 접근성 도구로 도착지가 미리 입력되어 있기 때문에, 안내 시작시 내부에 저장된 도착지를 전달한다.
        return destination ?: ""
    }

    @Throws(IOException::class)
    override suspend fun listPoiAddress(poiName: String): List<Poi> {
        val ratio = RemoteConfigUtil.getString("naverApiRatio").toIntOrNull() ?: 100
        if (Random.nextInt(100) < ratio) return listPoiAddressViaOpenApi(poiName)
        // fusion 은 비공개 API 라 토큰 스펙 변경으로 조용히 빈 결과가 될 수 있음 → 공식 API 로 폴백.
        return listPoiAddressViaFusionSearch(poiName).ifEmpty { listPoiAddressViaOpenApi(poiName) }
    }

    private suspend fun listPoiAddressViaOpenApi(poiName: String): List<Poi> {
        val poiList = mutableListOf<Poi>()
        val response = naverMapApi.search(poiName)
        if (!response.isSuccessful || response.body() == null) {
            AnalysisUtil.warn("naver api error: " + response.errorBody()?.string().orEmpty())
        }
        response.body()?.items?.let { items ->
            items.forEach { place ->
                poiList.add(
                    Poi(
                        poiName = place.name,
                        roadAddress = place.roadAddress.orEmpty(),
                        address = place.address,
                        longitude = place.longitude,
                        latitude = place.latitude,
                    ),
                )
            }
        }
        ResponseCloser.closeAll(response)
        return poiList
    }

    private suspend fun listPoiAddressViaFusionSearch(poiName: String): List<Poi> {
        val poiList = mutableListOf<Poi>()
        val credentials = NaverMobileWebTokenProvider.credentials(naverFusionSearchApi) ?: return poiList
        val response = naverFusionSearchApi.search(credentials.cookieHeader, credentials.accessToken, poiName)
        if (!response.isSuccessful || response.body() == null) {
            AnalysisUtil.warn("naver fusion search error: " + response.errorBody()?.string().orEmpty())
        }
        response.body()?.items?.let { items ->
            items.forEach { place ->
                poiList.add(
                    Poi(
                        poiName = place.name,
                        roadAddress = place.roadAddress.orEmpty(),
                        address = place.address,
                        longitude = place.longitude?.toString(),
                        latitude = place.latitude?.toString(),
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
        // 안내가 시작될 경우 도착지를 이용하여 전송한다. 목적지가 입력된지 특정 시간 내에만 동작한다.
        if (notificationText != GUIDANCE_TEXT) return IgnoreReason.TEXT_MISMATCH
        if (destination.isNullOrEmpty()) return IgnoreReason.NO_CAPTURE
        if (System.currentTimeMillis() - savedTime > DESTINATION_TTL_MS) return IgnoreReason.TTL_EXPIRED
        if (driveModeProvider?.invoke() == NaviDriveMode.SAFE_DRIVE) return IgnoreReason.SAFE_DRIVE
        return null
    }

    override fun consumeCapturedDestination() = clearDestination()

    companion object {
        private val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(HttpRetryInterceptor(10))
                .build()

        private val naverMapApi =
            Retrofit
                .Builder()
                .baseUrl("https://openapi.naver.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    httpClient
                        .newBuilder()
                        .addInterceptor(
                            Interceptor { chain: Interceptor.Chain ->
                                chain.proceed(
                                    chain
                                        .request()
                                        .newBuilder()
                                        .addHeader("X-Naver-Client-Id", RemoteConfigUtil.getString("naverClientId"))
                                        .addHeader("X-Naver-Client-Secret", RemoteConfigUtil.getString("naverClientSecret"))
                                        .build(),
                                )
                            },
                        ).build(),
                ).build()
                .create(NaverMapApi::class.java)

        private val naverFusionSearchApi =
            Retrofit
                .Builder()
                .baseUrl("https://svc-api.map.naver.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    httpClient
                        .newBuilder()
                        .addInterceptor(
                            Interceptor { chain: Interceptor.Chain ->
                                chain.proceed(
                                    chain
                                        .request()
                                        .newBuilder()
                                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                                        .build(),
                                )
                            },
                        ).build(),
                ).build()
                .create(NaverFusionSearchApi::class.java)

        // 접근성 도구로 판단한 목적지 임시 저장
        @Volatile private var destination: String? = null

        @Volatile private var savedTime = System.currentTimeMillis()

        private const val GUIDANCE_TEXT = "내비게이션 - 안내 중"

        @Volatile private var driveModeProvider: (() -> NaviDriveMode)? = null

        fun setDriveModeProvider(provider: (() -> NaviDriveMode)?) {
            driveModeProvider = provider
        }

        private val PLACEHOLDER_TEXTS =
            setOf(
                "도착지 입력",
                "출발지 입력",
                "경유지 입력",
                "Enter destination",
                "Enter starting point",
                "Enter stop",
                "目的地入力",
                "出発地入力",
                "経由地入力",
                "输入目的地",
                "输入出发地",
                "输入经由地",
            )

        private const val DESTINATION_TTL_MS = 60_000L

        fun isDestinationEmpty(): Boolean = destination.isNullOrEmpty()

        fun clearDestination() {
            destination = null
        }

        fun addDestination(dest: String) {
            val cleaned = TextNormalizer.normalize(dest)
            if (cleaned.isEmpty()) return
            if (PLACEHOLDER_TEXTS.any { it.equals(cleaned, ignoreCase = true) }) {
                destination = null
                return
            }
            if (cleaned == destination) {
                savedTime = System.currentTimeMillis()
                return
            }
            destination = cleaned
            savedTime = System.currentTimeMillis()
        }
    }
}
