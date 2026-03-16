package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.NaverFusionSearch
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NaverFusionSearchApi {
    @GET("/v1/fusion-search/all")
    suspend fun search(
        @Query("query") query: String,
        @Query("siteSort") siteSort: String = "relativity",
        @Query("petrolType") petrolType: String = "all",
        @Query("size") size: Int = 10,
        @Query("includes") includes: String = "address_polygon",
    ): Response<NaverFusionSearch.Response>
}
