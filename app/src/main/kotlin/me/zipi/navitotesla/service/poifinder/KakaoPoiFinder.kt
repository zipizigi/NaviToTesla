package me.zipi.navitotesla.service.poifinder

import android.util.Log
import me.zipi.navitotesla.api.KakaoMapApi
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

class KakaoPoiFinder : PoiFinder {
    override fun parseDestination(notificationText: String): String {/*
         * 목적지 : ~~~~
         */
        return notificationText.replace("목적지 : ", "").trim { it <= ' ' }
    }

    @Throws(IOException::class)
    override suspend fun listPoiAddress(poiName: String): List<Poi> {
        val poiList: MutableList<Poi> = ArrayList()
        val response = kakaoMapApi.search(poiName)
        if (!response.isSuccessful || response.body() == null) {
            Log.w(this.javaClass.name, "Kakao api error: " + response.errorBody())
            AnalysisUtil.log("Kakao api error: " + if (response.errorBody() == null) "" else response.errorBody()!!.string())
        }
        if (response.isSuccessful && response.body() != null) {
            val withLocalName = RemoteConfigUtil.getBoolean("withLocalName") // 법정동 포함 여부
            for (place in response.body()!!.documents) {
                val poi = Poi(
                    poiName = place.placeName,
                    roadAddress = place.getRoadAddressName(withLocalName),
                    address = place.addressName,
                    longitude = place.longitude,
                    latitude = place.latitude,
                )
                poiList.add(poi)
            }
        }
        ResponseCloser.closeAll(response)
        return poiList
    }

    override fun isIgnore(notificationTitle: String, notificationText: String): Boolean {
        return notificationTitle != "길안내 주행 중"
    }

    companion object {
        private val kakaoMapApi =
            Retrofit.Builder().baseUrl("https://dapi.kakao.com").addConverterFactory(GsonConverterFactory.create()).client(
                OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).addInterceptor(
                    Interceptor { chain: Interceptor.Chain ->
                        val request = chain.request().newBuilder().addHeader(
                            "Authorization",
                            "KakaoAK " + RemoteConfigUtil.getString("kakaoApiKey"),
                        ).build()
                        chain.proceed(request)
                    },
                ).addInterceptor(HttpRetryInterceptor(10)).build(),
            ).build().create(KakaoMapApi::class.java)
    }
}
