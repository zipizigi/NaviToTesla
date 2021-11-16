package me.zipi.navitotesla.api;

import me.zipi.navitotesla.BuildConfig;
import me.zipi.navitotesla.model.TMap;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface TMapApi {
    @GET("/tmap/pois?version=1&appKey=" + BuildConfig.TMAP_API_KEY)
    @Headers({
            "Accept: application/json"
    })
    Call<TMap.SearchPoiResponse> search(@Query("searchKeyword") String query);
}
