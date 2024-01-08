package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.NaverMap
import retrofit2.Response
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface NaverMapApi {
    @POST("/search2/searchMore.naver")
    @Headers("Accept: application/json")
    suspend fun search(@Query("query") query: String): Response<NaverMap.Response>
}