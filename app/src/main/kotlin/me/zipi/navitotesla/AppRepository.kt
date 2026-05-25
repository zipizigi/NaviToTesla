package me.zipi.navitotesla

import androidx.room.withTransaction
import me.zipi.navitotesla.api.TeslaApi
import me.zipi.navitotesla.api.TeslaAuthApi
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
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
                            AnalysisUtil.appendLog("DEBUG", it)
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
                            AnalysisUtil.appendLog("DEBUG", it)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    ).addInterceptor(HttpRetryInterceptor(20))
                    .build(),
            ).build()
            .create()

    suspend fun getPoiSync(
        poiName: String,
        packageName: String = "",
    ): PoiAddressEntity? {
        val byPackage =
            if (packageName.isNotEmpty()) {
                database.poiAddressDao().findPoiByPackage(poiName, packageName)
            } else {
                database.poiAddressDao().findPoiLatest(poiName)
            }
        if (byPackage != null) return byPackage
        return database.poiAddressDao().findRegisteredByPoi(poiName)
    }

    suspend fun savePoi(
        poi: Poi,
        registered: Boolean,
    ) {
        if (poi.poiName == null) {
            return
        }
        database.withTransaction {
            database.poiAddressDao().insertPoi(
                PoiAddressEntity(
                    poi = poi.poiName,
                    packageName = poi.packageName,
                    roadAddress = poi.getRoadAddress(),
                    jibunAddress = poi.getAddress(),
                    latitude = poi.latitude,
                    longitude = poi.longitude,
                    registered = registered,
                    isDuplicate = poi.isDuplicate,
                    sentMode = null,
                    created = Date(),
                ),
            )
        }
    }

    /**
     * Resolver 가 전송 모드 결정 후 호출. 기존 row 가 있으면 id/registered 유지하면서
     * sentMode/created 갱신. 없으면 신규 insert. isAddress 분기처럼 savePoi 가 선행
     * 호출되지 않은 케이스에서도 row 를 만들 수 있도록 Poi 전체를 받는다.
     */
    suspend fun markSent(
        poi: Poi,
        sentMode: String,
    ) {
        val poiName = poi.poiName ?: return
        database.withTransaction {
            val existing = database.poiAddressDao().findPoiByPackage(poiName, poi.packageName)
            database.poiAddressDao().insertPoi(
                PoiAddressEntity(
                    id = existing?.id,
                    poi = poiName,
                    packageName = poi.packageName,
                    roadAddress = poi.getRoadAddress(),
                    jibunAddress = poi.getAddress(),
                    latitude = poi.latitude,
                    longitude = poi.longitude,
                    registered = existing?.registered,
                    isDuplicate = poi.isDuplicate,
                    sentMode = sentMode,
                    created = Date(),
                ),
            )
        }
    }

    suspend fun clearExpiredPoi() {
        // remove expire poi. (20% over)
        val expireDate = (System.currentTimeMillis() - PoiAddressEntity.EXPIRE_DAY * 1000 * 60 * 60 * 24 * 1.2).toLong()
        database.poiAddressDao().findExpired(expireDate).forEach { entity ->
            database.withTransaction {
                database.poiAddressDao().delete(entity)
            }
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
