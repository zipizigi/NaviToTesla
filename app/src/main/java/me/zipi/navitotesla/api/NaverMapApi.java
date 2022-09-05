package me.zipi.navitotesla.api;

import me.zipi.navitotesla.model.NaverMap;
import retrofit2.Call;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface NaverMapApi {


    @POST("/search2/searchMore.naver")
    @Headers({
            "Accept: application/json"
    })
    Call<NaverMap.Response> search(@Query("query") String query);

}