package me.zipi.navitotesla

import android.content.Context
import androidx.lifecycle.LiveData
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

class AppRepository private constructor(context: Context, private val database: AppDatabase) {
    val teslaApi: TeslaApi

    val teslaAuthApi: TeslaAuthApi

    init {
        teslaApi = Retrofit.Builder()
            .baseUrl("https://owner-api.teslamotors.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                        val token = PreferencesUtil.loadToken(context)
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
        teslaAuthApi = Retrofit.Builder()
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
    }
    @Suppress("unused")
    fun getPoi(poiName: String): LiveData<PoiAddressEntity> {
        return database.poiAddressDao().findPoi(poiName)
    }

    fun getPoiSync(poiName: String): PoiAddressEntity? {
        return database.poiAddressDao().findPoiSync(poiName)
    }

    fun savePoi(poiName: String, address: String, registered: Boolean) {
        database.runInTransaction {
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

    fun clearExpiredPoi() {
        // remove expire poi. (20% over)
        val expireDate: Long =
            (Date().time - PoiAddressEntity.expireDay * 1000 * 60 * 60 * 24 * 1.2).toLong()
        for (entity in database.poiAddressDao().findExpired(expireDate)) {
            database.runInTransaction {
                database.poiAddressDao().delete(entity)
            }
        }
    }

    fun clearAllPoi() {
        database.runInTransaction {
            database.poiAddressDao().deleteAllNotRegistered()
        }
    }

    companion object {
        private var instance: AppRepository? = null
        fun getInstance(context: Context, database: AppDatabase): AppRepository {
            if (instance == null) {
                synchronized(AppRepository::class.java) {
                    if (instance == null) {
                        instance = AppRepository(context, database)
                    }
                }
            }
            return instance!!
        }
    }
}