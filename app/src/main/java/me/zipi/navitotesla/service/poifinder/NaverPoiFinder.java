package me.zipi.navitotesla.service.poifinder;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.zipi.navitotesla.api.NaverMapApi;
import me.zipi.navitotesla.model.NaverMap;
import me.zipi.navitotesla.model.Poi;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.HttpRetryInterceptor;
import me.zipi.navitotesla.util.RemoteConfigUtil;
import me.zipi.navitotesla.util.ResponseCloser;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * naver navi poi finder
 * 화면이 변경되면, 접근성 도구로 목적지를 내부 변수(destination)에 저장해둔다. 이후 안내가 시작되면 해당 목적지를 전송한다.
 */
public class NaverPoiFinder implements PoiFinder {

    private static final NaverMapApi naverMapApi = new Retrofit.Builder()
            .baseUrl("https://m.map.naver.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor(new HttpRetryInterceptor(10))
                    .build())
            .build().create(NaverMapApi.class);


    // 접근성 도구로 판단한 목적지 임시 저장
    private static String destination;
    private static long savedTime = System.currentTimeMillis();


    public static void addDestination(String dest) {
        // 접근성 도구로 도착지가 비어있을 경우 임시 변수 초기화
        if (dest.trim().equalsIgnoreCase("도착지 입력")) {
            destination = "";
            return;
        }
        // 도착지가 입력
        destination = dest.trim();
        savedTime = System.currentTimeMillis();
    }

    @Override
    public String parseDestination(String notificationText) {
        // 접근성 도구로 도착지가 미리 입력되어 있기 때문에, 안내 시작시 내부에 저장된 도착지를 전달한다.
        return destination == null ? "" : destination;
    }

    @Override
    public List<Poi> listPoiAddress(String poiName) throws IOException {


        List<Poi> poiList = new ArrayList<>();
        Response<NaverMap.Response> response = naverMapApi.search(String.format("\"%s\"", poiName)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            Log.w(this.getClass().getName(), "naver api error: " + response.errorBody());
            AnalysisUtil.log("naver api error: " + (response.errorBody() == null ? "" : response.errorBody().string()));
        }


        if (response.isSuccessful() && response.body() != null && response.body().getResult() != null
                && response.body().getResult().getSite() != null && response.body().getResult().getSite().getList() != null) {
            boolean withLocalName = RemoteConfigUtil.getBoolean("withLocalName"); // 법정동 포함 여부
            for (NaverMap.Place place : response.body().getResult().getSite().getList()) {
                Poi poi = Poi.builder()
                        .poiName(place.getName())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .roadAddress(place.getRoadAddressName(withLocalName))
                        .address(place.getAddress())
                        .build();
                poiList.add(poi);
            }
        }
        ResponseCloser.closeAll(response);
        return poiList;
    }

    @Override
    public boolean isIgnore(String notificationTitle, String notificationText) {
        // 안내가 시작될 경우 도착지를 이용하여 전송한다. 목적지가 입력된지 특정 시간 내에만 동작한다.
        return !notificationText.equals("내비게이션 - 안내 중") || destination == null || destination.length() == 0
                || System.currentTimeMillis() - savedTime > 3 * 60 * 1000;
    }
}
