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
     * Resolver 가 검색 가능 여부 결정 후 호출. 기존 row 가 있으면 id/registered/sentMode 유지하면서
     * searchable 갱신. 없으면 신규 insert. 즐겨찾기(registered=true) row 의 sentMode 는 보존.
     */
    suspend fun markClassified(
        poi: Poi,
        searchable: Boolean?,
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
                    registered = existing?.registered ?: false,
                    isDuplicate = poi.isDuplicate,
                    sentMode = existing?.sentMode, // 즐겨찾기 명시 mode 보존
                    searchable = searchable,
                    created = Date(),
                ),
            )
        }
    }

    suspend fun clearExpiredPoi() {
        // remove expire poi. (20% over)
        val ttlMs = PoiAddressEntity.EXPIRE_DAY.toLong() * 24L * 60L * 60L * 1000L
        val expireDate = System.currentTimeMillis() - ttlMs * 12L / 10L
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
