package me.zipi.navitotesla.service;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.zipi.navitotesla.api.TMapApi;
import me.zipi.navitotesla.model.Poi;
import me.zipi.navitotesla.model.TMap;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.HttpRetryInterceptor;
import me.zipi.navitotesla.util.RemoteConfigUtil;
import me.zipi.navitotesla.util.ResponseCloser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TMapPoiFinder implements PoiFinder {
    private static final TMapApi tMapApi = new Retrofit.Builder()
            .baseUrl("https://apis.openapi.sk.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request request = chain.request().newBuilder()
                                .url(chain.request().url().newBuilder()
                                        .addQueryParameter("version", "1")
                                        .addQueryParameter("appKey", RemoteConfigUtil.getConfig("tmapApiKey"))
                                        .build())
                                .build();
                        return chain.proceed(request);
                    })
                    .addInterceptor(new HttpRetryInterceptor(10))
                    .build())

            .build().create(TMapApi.class);


    @Override
    public String parseDestination(String notificationText) {

        /*
            안심주행
            출발지 > 목적지
            출발지 > 경유지 > 목적지
            출발지 > 경유지1 > 경유지2 > 목적지
         */
        return notificationText.split(">")[notificationText.split(">").length - 1].trim();

    }

    @Override
    public List<Poi> listPoiAddress(String poiName) throws IOException {

        List<Poi> listPoi = new ArrayList<>();
        Response<TMap.SearchPoiResponse> response = tMapApi.search(poiName).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(this.getClass().getName(), "Tmap api error: " + response.errorBody());
            AnalysisUtil.log("Tmap api error: " + response.errorBody());

        }

        if (response.isSuccessful() && response.body() != null && response.body().getSearchPoiInfo() != null) {
            for (TMap.PoiItem item : response.body().getSearchPoiInfo().getPois().getPoi()) {
                Poi poi = Poi.builder()
                        .poiName(item.getName())
                        .address(item.getAddress())
                        .roadAddress(item.getRoadAddress())
                        .latitude(item.getLatitude())
                        .longitude(item.getLongitude())
                        .build();
                listPoi.add(poi);
            }
        }

        ResponseCloser.closeAll(response);

        return listPoi;
    }

    @Override
    public boolean isIgnore(String notificationTitle, String notificationText) {
        return notificationText.equals("안심주행") || !notificationTitle.equals("경로주행");
    }

}
