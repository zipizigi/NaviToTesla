package me.zipi.navitotesla.service;

import android.util.Log;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import me.zipi.navitotesla.api.TMapApi;
import me.zipi.navitotesla.exception.DuplicatePoiException;
import me.zipi.navitotesla.model.TMap;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.util.PreferencesUtil;
import me.zipi.navitotesla.util.RemoteConfigUtil;
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
                    .connectTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .addInterceptor(chain -> {
                        Request request = chain.request().newBuilder()
                                .url(chain.request().url().newBuilder()
                                        .addQueryParameter("version", "1")
                                        .addQueryParameter("appKey", RemoteConfigUtil.getConfig("tmapApiKey"))
                                        .build())
                                .build();
                        return chain.proceed(request);
                    })
                    .build())
            .build().create(TMapApi.class);

    @Override
    public String findPoiAddress(String poiName) throws DuplicatePoiException, IOException {
        String address = "";
        Response<TMap.SearchPoiResponse> response = tMapApi.search(poiName).execute();

        if (!response.isSuccessful() || response.body() == null) {
            Log.w(this.getClass().getName(), "Tmap api error: " + response.errorBody());
            return "";
        }

        int sameCount = 0;

        for (TMap.PoiItem poi : response.body().getSearchPoiInfo().getPois().getPoi()) {
            if (poi.getName().equalsIgnoreCase(poiName)) {
                sameCount++;
                if (!poi.getRoadAddress().equals("")) {
                    // 도로명
                    address = poi.getRoadAddress();
                } else if (!poi.getAddress().equals("")) {
                    // 지번
                    address = poi.getAddress();
                } else {
                    // gps
                    address = String.format(Locale.getDefault(), "%s,%s", poi.getLatitude(), poi.getLongitude());
                }

            }
        }
        if (sameCount > 1) {
            // 중복지명이 있으므로 전송 안함.
            throw new DuplicatePoiException(poiName);
        }

        return address;
    }

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
    public boolean isIgnore(String notificationTitle, String notificationText) {
        return notificationText.equals("안심주행");
    }

}
