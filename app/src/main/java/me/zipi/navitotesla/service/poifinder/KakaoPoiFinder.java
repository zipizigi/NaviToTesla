package me.zipi.navitotesla.service.poifinder;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.zipi.navitotesla.api.KakaoMapApi;
import me.zipi.navitotesla.model.KakaoMap;
import me.zipi.navitotesla.model.Poi;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.HttpRetryInterceptor;
import me.zipi.navitotesla.util.RemoteConfigUtil;
import me.zipi.navitotesla.util.ResponseCloser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class KakaoPoiFinder implements PoiFinder {
    private static final KakaoMapApi kakaoMapApi = new Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request request = chain.request().newBuilder()
                                .addHeader("Authorization", "KakaoAK " + RemoteConfigUtil.getConfig("kakaoApiKey"))
                                .build();
                        return chain.proceed(request);
                    })
                    .addInterceptor(new HttpRetryInterceptor(10))
                    .build())
            .build().create(KakaoMapApi.class);


    @Override
    public String parseDestination(String notificationText) {
        /*
         * 목적지 : ~~~~
         */
        return notificationText.replace("목적지 : ", "").trim();
    }

    @Override
    public List<Poi> listPoiAddress(String poiName) throws IOException {
        List<Poi> poiList = new ArrayList<>();
        Response<KakaoMap.Response<KakaoMap.Place>> response = kakaoMapApi.search(poiName).execute();
        if (!response.isSuccessful() || response.body() == null) {
            Log.w(this.getClass().getName(), "Kakao api error: " + response.errorBody());
            AnalysisUtil.log("Kakao api error: " + (response.errorBody() == null ? "" : response.errorBody().string()));
        }


        if (response.isSuccessful() && response.body() != null && response.body().getDocuments() != null) {
            for (KakaoMap.Place place : response.body().getDocuments()) {
                Poi poi = Poi.builder()
                        .poiName(place.getPlaceName())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .roadAddress(place.getRoadAddressName())
                        .address(place.getAddressName())
                        .build();
                poiList.add(poi);

            }
        }
        ResponseCloser.closeAll(response);
        return poiList;
    }

    @Override
    public boolean isIgnore(String notificationTitle, String notificationText) {
        return notificationTitle.equals("안전운전 주행 중");
    }
}
