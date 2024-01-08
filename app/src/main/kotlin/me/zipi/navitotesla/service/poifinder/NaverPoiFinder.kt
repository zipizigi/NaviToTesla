package me.zipi.navitotesla.service.poifinder

import android.util.Log
import me.zipi.navitotesla.api.NaverMapApi
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.HttpRetryInterceptor
import me.zipi.navitotesla.util.RemoteConfigUtil
import me.zipi.navitotesla.util.ResponseCloser
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * naver navi poi finder
 * 화면이 변경되면, 접근성 도구로 목적지를 내부 변수(destination)에 저장해둔다. 이후 안내가 시작되면 해당 목적지를 전송한다.
 */
class NaverPoiFinder : PoiFinder {
    override fun parseDestination(notificationText: String): String {
        // 접근성 도구로 도착지가 미리 입력되어 있기 때문에, 안내 시작시 내부에 저장된 도착지를 전달한다.
        return if (destination == null) "" else destination!!
    }

    @Throws(IOException::class)
    override suspend fun listPoiAddress(poiName: String): List<Poi> {
        val poiList: MutableList<Poi> = ArrayList()
        val response = naverMapApi.search(String.format("\"%s\"", poiName))
        if (!response.isSuccessful || response.body() == null) {
            Log.w(this.javaClass.name, "naver api error: " + response.errorBody())
            AnalysisUtil.log("naver api error: " + if (response.errorBody() == null) "" else response.errorBody()!!.string())
        }
        if (response.isSuccessful && response.body()?.result?.site?.list != null) {
            val withLocalName = RemoteConfigUtil.getBoolean("withLocalName") // 법정동 포함 여부
            for (place in response.body()!!.result!!.site!!.list!!) {
                val poi = Poi(
                    poiName = place.name,
                    roadAddress = place.getRoadAddressName(withLocalName),
                    address = place.address,
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
        // 안내가 시작될 경우 도착지를 이용하여 전송한다. 목적지가 입력된지 특정 시간 내에만 동작한다.
        return notificationText != "내비게이션 - 안내 중" || destination == null || destination!!.isEmpty() || System.currentTimeMillis() - savedTime > 3 * 60 * 1000
    }

    companion object {
        private val naverMapApi =
            Retrofit.Builder().baseUrl("https://m.map.naver.com").addConverterFactory(GsonConverterFactory.create()).client(
                OkHttpClient.Builder().connectTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor(HttpRetryInterceptor(10)).build(),
            ).build().create(NaverMapApi::class.java)

        // 접근성 도구로 판단한 목적지 임시 저장
        private var destination: String? = null
        private var savedTime = System.currentTimeMillis()
        fun addDestination(dest: String) {
            // 접근성 도구로 도착지가 비어있을 경우 임시 변수 초기화
            if (dest.trim { it <= ' ' }.equals("도착지 입력", ignoreCase = true)) {
                destination = ""
                return
            }
            // 도착지가 입력
            destination = dest.trim { it <= ' ' }
            savedTime = System.currentTimeMillis()
        }
    }
}
