package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.NaverMap
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NaverMapApi {
    @GET("/v1/search/local.json")
    suspend fun search(
        @Query("query") query: String,
        @Query("display") display: Int = 5,
    ): Response<NaverMap.Response>
}
