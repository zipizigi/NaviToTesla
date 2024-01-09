package me.zipi.navitotesla

import androidx.room.withTransaction
import me.zipi.navitotesla.api.TeslaApi
import me.zipi.navitotesla.api.TeslaAuthApi
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
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

class AppRepository private constructor(private val database: AppDatabase) {
    val teslaApi: TeslaApi =
        Retrofit.Builder()
            .baseUrl("https://owner-api.teslamotors.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(
                        Interceptor { chain: Interceptor.Chain ->
                            val token = PreferencesUtil.loadTokenSync()
                            val accessToken = token?.accessToken ?: ""
                            val request = chain.request().newBuilder()
                                .addHeader("User-Agent", "NaviToTesla/${BuildConfig.VERSION_CODE}")
                                .addHeader("Accept", "*/*")
                                .addHeader("Authorization", "Bearer $accessToken")
                                .build()
                            chain.proceed(request)
                        },
                    )
                    .addInterceptor(
                        HttpLoggingInterceptor {
                            AnalysisUtil.appendLog("DEBUG", it)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                    .addInterceptor(HttpRetryInterceptor(20))
                    .build(),
            ).build()
            .create()

    val teslaAuthApi: TeslaAuthApi =
        Retrofit.Builder()
            .baseUrl("https://auth.tesla.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(
                        Interceptor { chain: Interceptor.Chain ->
                            val request = chain.request().newBuilder()
                                .addHeader("User-Agent", "NaviToTesla/${BuildConfig.VERSION_CODE}")
                                .build()
                            chain.proceed(request)
                        },
                    )
                    .addInterceptor(
                        HttpLoggingInterceptor {
                            AnalysisUtil.appendLog("DEBUG", it)
                        }.apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                    .addInterceptor(HttpRetryInterceptor(20))
                    .build(),
            ).build()
            .create()

    suspend fun getPoiSync(poiName: String): PoiAddressEntity? {
        return database.poiAddressDao().findPoi(poiName)
    }

    suspend fun savePoi(poiName: String, address: String, registered: Boolean) {
        database.withTransaction {
            database.poiAddressDao().insertPoi(
                PoiAddressEntity(
                    poi = poiName,
                    address = address,
                    registered = registered,
                    created = Date(),
                ),
            )
        }
    }

    suspend fun clearExpiredPoi() {
        // remove expire poi. (20% over)
        val expireDate: Long = (Date().time - PoiAddressEntity.expireDay * 1000 * 60 * 60 * 24 * 1.2).toLong()
        for (entity in database.poiAddressDao().findExpired(expireDate)) {
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

        fun getInstance(): AppRepository {
            return instance
        }
    }
}
