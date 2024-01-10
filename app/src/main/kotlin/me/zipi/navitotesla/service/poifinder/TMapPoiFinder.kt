package me.zipi.navitotesla.service.poifinder

import android.util.Log
import me.zipi.navitotesla.api.TMapApi
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.HttpRetryInterceptor
import me.zipi.navitotesla.util.RemoteConfigUtil
import me.zipi.navitotesla.util.ResponseCloser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class TMapPoiFinder : PoiFinder {
    override fun parseDestination(notificationText: String): String {
        /*
            안심주행
            출발지 > 목적지
            출발지 > 경유지 > 목적지
            출발지 > 경유지1 > 경유지2 > 목적지
         */
        return notificationText.split(">".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[
            notificationText.split(">".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size - 1,
        ].trim { it <= ' ' }
    }

    @Throws(IOException::class)
    override suspend fun listPoiAddress(poiName: String): List<Poi> {
        val listPoi: MutableList<Poi> = ArrayList()
        val response = tMapApi.search(poiName)
        if (!response.isSuccessful || response.body() == null) {
            Log.w(this.javaClass.name, "Tmap api error: " + response.errorBody())
            AnalysisUtil.log("Tmap api error: " + response.errorBody())
        }
        if (response.isSuccessful && response.body()?.searchPoiInfo != null) {
            val withLocalName = RemoteConfigUtil.getBoolean("withLocalName") // 법정동 포함 여부
            for (item in response.body()!!.searchPoiInfo!!.pois.poi) {
                val poi =
                    Poi(
                        poiName = item.name,
                        roadAddress = item.getRoadAddress(withLocalName),
                        address = item.address,
                        longitude = item.latitude,
                        latitude = item.longitude,
                    )

                listPoi.add(poi)
            }
        }
        ResponseCloser.closeAll(response)
        return listPoi
    }

    override fun isIgnore(
        notificationTitle: String,
        notificationText: String,
    ): Boolean {
        return notificationText == "안심주행" || notificationTitle != "경로주행"
    }

    companion object {
        private val tMapApi =
            Retrofit.Builder()
                .baseUrl("https://apis.openapi.sk.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(
                            Interceptor { chain: Interceptor.Chain ->
                                val request =
                                    chain.request().newBuilder().url(
                                        chain.request().url.newBuilder().addQueryParameter("version", "1").addQueryParameter(
                                            "appKey",
                                            RemoteConfigUtil.getString("tmapApiKey"),
                                        ).build(),
                                    ).build()
                                chain.proceed(request)
                            },
                        )
                        .addInterceptor(HttpRetryInterceptor(10))
                        .build(),
                ).build()
                .create(TMapApi::class.java)
    }
}
