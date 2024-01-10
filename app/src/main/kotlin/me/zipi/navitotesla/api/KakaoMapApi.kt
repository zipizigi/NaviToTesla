package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.KakaoMap
import retrofit2.Response
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface KakaoMapApi {
    @POST("/v2/local/search/keyword.json")
    @Headers("Accept: application/json")
    suspend fun search(
        @Query("query") query: String,
    ): Response<KakaoMap.Response<KakaoMap.Place>>
}
