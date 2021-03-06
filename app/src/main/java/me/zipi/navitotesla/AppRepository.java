package me.zipi.navitotesla;

import android.content.Context;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import androidx.lifecycle.LiveData;
import lombok.Getter;
import me.zipi.navitotesla.api.TeslaApi;
import me.zipi.navitotesla.api.TeslaAuthApi;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.util.HttpRetryInterceptor;
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


    private AppRepository(final Context context, final AppDatabase database) {
        this.database = database;

        teslaApi = new Retrofit.Builder()
                .baseUrl("https://owner-api.teslamotors.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(chain -> {
                            Token token = PreferencesUtil.loadToken(context);
                            String accessToken = token == null ? "" : token.getAccessToken();
                            Request request = chain.request().newBuilder()
                                    .addHeader("User-Agent", "Navi_To_Tesla")
                                    .addHeader("Accept", "*/*")
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Authorization", "Bearer " + accessToken)
                                    .build();
                            return chain.proceed(request);
                        })
                        .addInterceptor(new HttpRetryInterceptor(20))
                        .build())
                .build().create(TeslaApi.class);
        teslaAuthApi = new Retrofit.Builder()
                .baseUrl("https://auth.tesla.com")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .addInterceptor(chain -> {
                            Request request = chain.request().newBuilder()
                                    .addHeader("User-Agent", "Navi_To_Tesla")
                                    .addHeader("Accept", "*/*")
                                    .addHeader("Content-Type", "application/json")
                                    .build();
                            return chain.proceed(request);
                        })
                        .addInterceptor(new HttpRetryInterceptor(20))
                        .build())
                .build().create(TeslaAuthApi.class);

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


    public LiveData<PoiAddressEntity> getPoi(String poiName) {
        return database.poiAddressDao().findPoi(poiName);
    }


    public PoiAddressEntity getPoiSync(String poiName) {
        return database.poiAddressDao().findPoiSync(poiName);
    }

    public void savePoi(String poiName, String address, Boolean registered) {
        database.runInTransaction(() ->
                database.poiAddressDao().insertPoi(PoiAddressEntity.builder()
                        .poi(poiName)
                        .address(address)
                        .created(Calendar.getInstance().getTime())
                        .registered(registered)
                        .build())
        );

    }

    public void clearExpiredPoi() {
        // remove expire poi. (20% over)
        long expireDate = (long) (Calendar.getInstance().getTime().getTime() - PoiAddressEntity.expireDay * 1000 * 60 * 60 * 24 * 1.2);
        for (PoiAddressEntity entity : database.poiAddressDao().findExpired(expireDate)) {
            database.runInTransaction(() -> database.poiAddressDao().delete(entity));
        }
    }

    public void clearAllPoi() {
        database.runInTransaction(() -> database.poiAddressDao().deleteAllNotRegistered());
    }
}
