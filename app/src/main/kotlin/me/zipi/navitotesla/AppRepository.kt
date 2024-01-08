package me.zipi.navitotesla

import androidx.room.withTransaction
import me.zipi.navitotesla.api.TeslaApi
import me.zipi.navitotesla.api.TeslaAuthApi
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.util.HttpRetryInterceptor
import me.zipi.navitotesla.util.PreferencesUtil
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit

class AppRepository private constructor(private val database: AppDatabase) {
    val teslaApi: TeslaApi = Retrofit.Builder()
        .baseUrl("https://owner-api.teslamotors.com")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val token = PreferencesUtil.loadTokenSync()
                    val accessToken = token?.accessToken ?: ""
                    val request = chain.request().newBuilder()
                        .addHeader("User-Agent", "Navi_To_Tesla")
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    chain.proceed(request)
                })
                .addInterceptor(HttpRetryInterceptor(20))
                .build()
        )
        .build().create(TeslaApi::class.java)

    val teslaAuthApi: TeslaAuthApi = Retrofit.Builder()
        .baseUrl("https://auth.tesla.com")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("User-Agent", "Navi_To_Tesla")
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .build()
                    chain.proceed(request)
                })
                .addInterceptor(HttpRetryInterceptor(20))
                .build()
        )
        .build().create(TeslaAuthApi::class.java)
    
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
                    created = Date()
                )
            )
        }
    }

    suspend fun clearExpiredPoi() {
        // remove expire poi. (20% over)
        val expireDate: Long =
            (Date().time - PoiAddressEntity.expireDay * 1000 * 60 * 60 * 24 * 1.2).toLong()
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