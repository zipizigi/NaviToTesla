package me.zipi.navitotesla

import androidx.room.withTransaction
import me.zipi.navitotesla.api.TeslaApi
import me.zipi.navitotesla.api.TeslaAuthApi
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.service.place.Searchability
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.HttpRetryInterceptor
import me.zipi.navitotesla.util.PreferencesUtil
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.util.Date
import java.util.concurrent.TimeUnit

class AppRepository private constructor(
    private val database: AppDatabase,
) {
    val teslaApi: TeslaApi =
        Retrofit
            .Builder()
            .baseUrl("https://owner-api.teslamotors.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(
                        Interceptor { chain: Interceptor.Chain ->
                            val token = PreferencesUtil.loadTokenSync()
                            val accessToken = token?.accessToken ?: ""
                            val request =
                                chain
                                    .request()
                                    .newBuilder()
                                    .addHeader("User-Agent", "NaviToTesla/${BuildConfig.VERSION_CODE}")
                                    .addHeader("Accept", "*/*")
                                    .addHeader("Authorization", "Bearer $accessToken")
                                    .build()
                            chain.proceed(request)
                        },
                    ).addInterceptor(
                        HttpLoggingInterceptor {
                            AnalysisUtil.debug(it)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    ).addInterceptor(HttpRetryInterceptor(20))
                    .build(),
            ).build()
            .create()

    val teslaAuthApi: TeslaAuthApi =
        Retrofit
            .Builder()
            .baseUrl("https://auth.tesla.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(
                        Interceptor { chain: Interceptor.Chain ->
                            val request =
                                chain
                                    .request()
                                    .newBuilder()
                                    .addHeader("User-Agent", "NaviToTesla/${BuildConfig.VERSION_CODE}")
                                    .build()
                            chain.proceed(request)
                        },
                    ).addInterceptor(
                        HttpLoggingInterceptor {
                            AnalysisUtil.debug(it)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    ).addInterceptor(HttpRetryInterceptor(20))
                    .build(),
            ).build()
            .create()

    /**
     * 우선순위 (1차 매칭 후 즉시 반환):
     *   1) 같은 packageName 의 row (favorite or cache)
     *   2) packageName 비어있는 글로벌 favorite (NULL 포함 — v1~v5 마이그레이션 row 보호)
     *   3) 다른 packageName 의 favorite — 즐겨찾기는 navi 무관하게 같은 장소로 본다
     */
    suspend fun getPoiSync(
        poiName: String,
        packageName: String = "",
    ): PoiAddressEntity? {
        val rows = database.poiAddressDao().findAllByPoi(poiName)
        if (rows.isEmpty()) return null

        if (packageName.isNotEmpty()) {
            rows.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        rows.firstOrNull { it.isRegistered() && it.packageName.isNullOrEmpty() }?.let { return it }
        return rows.firstOrNull { it.isRegistered() }
    }

    suspend fun savePoi(
        poi: Poi,
        registered: Boolean,
    ) {
        if (poi.poiName == null) {
            return
        }

        val road = poi.getRoadAddress().sanitizeAddressOrNull()
        val jibun = poi.getAddress().sanitizeAddressOrNull()
        val pkg = poi.packageName.ifBlank { "" }
        database.withTransaction {
            database.poiAddressDao().insertPoi(
                PoiAddressEntity(
                    poi = poi.poiName,
                    packageName = pkg,
                    roadAddress = road,
                    jibunAddress = jibun,
                    latitude = poi.latitude?.takeUnless { it.isBlank() },
                    longitude = poi.longitude?.takeUnless { it.isBlank() },
                    registered = registered,
                    isDuplicate = poi.isDuplicate,
                    created = Date(),
                ),
            )
        }
    }

    suspend fun markClassified(
        poi: Poi,
        searchability: Searchability,
    ) {
        val poiName = poi.poiName ?: return
        val searchable: Boolean? =
            when (searchability) {
                Searchability.Searchable -> true
                Searchability.NotSearchable -> false
                Searchability.Unknown -> null
            }
        database.poiAddressDao().updateClassification(
            poi = poiName,
            packageName = matchPackageName(poi),
            searchable = searchable,
            now = System.currentTimeMillis(),
        )
    }

    private fun String.sanitizeAddressOrNull(): String? {
        val t = trim()
        if (t.isEmpty()) return null
        if (t == "null,null") return null
        return t
    }

    suspend fun touchLastUsed(poi: Poi) {
        val poiName = poi.poiName ?: return
        database.poiAddressDao().updateLastUsedAt(poiName, matchPackageName(poi), System.currentTimeMillis())
    }

    private fun matchPackageName(poi: Poi): String = if (poi.isFavorite) "" else poi.packageName

    suspend fun clearExpiredPoi() {
        val ttlMs = PoiAddressEntity.EXPIRE_DAY.toLong() * 24L * 60L * 60L * 1000L
        val expireDate = System.currentTimeMillis() - ttlMs
        try {
            database.poiAddressDao().deleteExpired(expireDate)
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
        }
    }

    suspend fun clearAllPoi() {
        database.withTransaction {
            database.poiAddressDao().deleteAllNotRegistered()
        }
    }

    companion object {
        private lateinit var instance: AppRepository

        fun initialize(database: AppDatabase) {
            if (!this::instance.isInitialized) {
                synchronized(AppDatabase::class) {
                    instance = AppRepository(database)
                }
            }
        }

        fun isInitialized(): Boolean = this::instance.isInitialized

        fun getInstance(): AppRepository = instance
    }
}
