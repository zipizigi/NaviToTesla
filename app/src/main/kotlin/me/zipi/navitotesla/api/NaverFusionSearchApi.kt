package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.NaverFusionSearch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NaverFusionSearchApi {
    @POST("/v1/auth/token")
    suspend fun issueAccessToken(
        @Header("Cookie") cookie: String,
        @Body body: NaverFusionSearch.TokenRequest,
    ): Response<NaverFusionSearch.TokenResponse>

    /** accessToken 은 쿠키를 proof 로 검증하므로 검색 요청에도 쿠키를 함께 보내야 함. */
    @GET("/v1/fusion-search/all")
    suspend fun search(
        @Header("Cookie") cookie: String,
        @Header("x-maps-mobileweb-token") accessToken: String,
        @Query("query") query: String,
        @Query("siteSort") siteSort: String = "relativity",
        @Query("petrolType") petrolType: String = "all",
        @Query("size") size: Int = 10,
        @Query("includes") includes: String = "address_polygon",
    ): Response<NaverFusionSearch.Response>
}
