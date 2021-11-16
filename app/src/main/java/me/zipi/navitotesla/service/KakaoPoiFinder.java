package me.zipi.navitotesla.service;

import android.util.Log;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import me.zipi.navitotesla.AppRepository;
import me.zipi.navitotesla.api.KakaoMapApi;
import me.zipi.navitotesla.model.KakaoMap;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class KakaoPoiFinder implements PoiFinder {
    private static final KakaoMapApi kakaoMapApi = new Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .build())
            .build().create(KakaoMapApi.class);

    @Override
    public String findPoiAddress(String poiName) {
        String address = "";

        try {
            Response<KakaoMap.Response<KakaoMap.Place>> response = kakaoMapApi.search(poiName).execute();

            if (response.isSuccessful() && response.body() != null && response.body().getDocuments() != null) {
                int sameCount = 0;
                for (KakaoMap.Place place : response.body().getDocuments()) {
                    if (place.getPlaceName().equalsIgnoreCase(poiName)) {
                        sameCount++;

                        if (!place.getRoadAddressName().equals("")) {
                            // 도로명
                            address = place.getRoadAddressName();
                        } else if (!place.getAddressName().equals("")) {
                            // 지번
                            address = place.getAddressName();
                        } else {
                            // gps
                            address = String.format(Locale.getDefault(), "%s,%s", place.getLatitude(), place.getLongitude());
                        }
                    }
                }

                if (sameCount > 1) {
                    // 중복지명 전송 안함
                    return "";
                }
            }
        } catch (Exception e) {
            Log.w(this.getClass().getName(), "kakao api call fail", e);
        }

        return address;
    }

    @Override
    public String parseDestination(String notificationText) {
        /*
         * 목적지 : ~~~~
         */
        return notificationText.replace("목적지 : ", "").trim();
    }

    @Override
    public boolean isIgnore(String notificationTitle, String notificationText) {
        return notificationTitle.equals("안전운전 주행 중");
    }
}
