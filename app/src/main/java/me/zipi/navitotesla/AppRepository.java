package me.zipi.navitotesla;

import android.content.Context;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import androidx.lifecycle.LiveData;
import lombok.Getter;
import me.zipi.navitotesla.api.KakaoMapApi;
import me.zipi.navitotesla.api.TeslaApi;
import me.zipi.navitotesla.api.TeslaAuthApi;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.util.PreferencesUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppRepository {
    private static AppRepository instance;

    private final AppDatabase database;

    @Getter
    private final TeslaApi teslaApi;
    @Getter
    private final TeslaAuthApi teslaAuthApi;

    @Getter
    private final KakaoMapApi kakaoMapApi;

    private AppRepository(final Context context, final AppDatabase database) {
        this.database = database;

        teslaApi = new Retrofit.Builder()
                .baseUrl("https://owner-api.teslamotors.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .connectTimeout(1, TimeUnit.MINUTES)
                        .readTimeout(1, TimeUnit.MINUTES)
                        .addInterceptor(chain -> {
                            Token token = PreferencesUtil.loadToken(context);
                            String accessToken = token == null ? "" : token.getAccessToken();
                            Request request = chain.request().newBuilder()
                                    .addHeader("User-Agent", "TMap_To_Tesla")
                                    .addHeader("Accept", "*/*")
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Authorization", "Bearer " + accessToken)
                                    .build();
                            return chain.proceed(request);
                        })
                        .build())
                .build().create(TeslaApi.class);
        teslaAuthApi = new Retrofit.Builder()
                .baseUrl("https://auth.tesla.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .connectTimeout(1, TimeUnit.MINUTES)
                        .readTimeout(1, TimeUnit.MINUTES)
                        .addInterceptor(chain -> {
                            Request request = chain.request().newBuilder()
                                    .addHeader("User-Agent", "TMap_To_Tesla")
                                    .addHeader("Accept", "*/*")
                                    .addHeader("Content-Type", "application/json")
                                    .build();
                            return chain.proceed(request);
                        })
                        .build())
                .build().create(TeslaAuthApi.class);

        kakaoMapApi = new Retrofit.Builder()
                .baseUrl("https://dapi.kakao.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .addInterceptor(chain -> {
                            Request request = chain.request().newBuilder()
                                    .addHeader("Authorization", "KakaoAK " + BuildConfig.KAKAO_API_KEY)
                                    .build();
                            return chain.proceed(request);
                        })
                        .build())
                .build().create(KakaoMapApi.class);
    }

    public static AppRepository getInstance(final Context context, final AppDatabase database) {
        if (instance == null) {
            synchronized (AppRepository.class) {
                if (instance == null) {
                    instance = new AppRepository(context, database);
                }
            }
        }
        return instance;
    }

    public static AppRepository getInstance() {
        // null check!!
        return instance;
    }

    public LiveData<PoiAddressEntity> getPoi(String poiName) {
        return database.poiAddressDao().findPoi(poiName);
    }


    public PoiAddressEntity getPoiSync(String poiName) {
        return database.poiAddressDao().findPoiSync(poiName);
    }

    public void savePoi(String poiName, String address) {
        database.runInTransaction(() ->
                database.poiAddressDao().insertPoi(PoiAddressEntity.builder()
                        .poi(poiName)
                        .address(address)
                        .created(Calendar.getInstance().getTime())
                        .build())
        );

    }
}
