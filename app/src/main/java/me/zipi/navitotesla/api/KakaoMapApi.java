package me.zipi.navitotesla.api;

import me.zipi.navitotesla.model.KakaoMap;
import retrofit2.Call;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface KakaoMapApi {
    @POST("/v2/local/search/keyword.json")
    @Headers({
            "Accept: application/json"
    })
    Call<KakaoMap.Response<KakaoMap.Place>> search(@Query("query") String query);
}
