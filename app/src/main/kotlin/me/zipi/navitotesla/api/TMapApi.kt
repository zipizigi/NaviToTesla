package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.TMap.SearchPoiResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface TMapApi {
    @GET("/tmap/pois")
    @Headers("Accept: application/json")
    fun search(@Query("searchKeyword") query: String?): Call<SearchPoiResponse?>
}