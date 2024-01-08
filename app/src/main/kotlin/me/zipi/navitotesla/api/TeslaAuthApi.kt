package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.TeslaRefreshTokenRequest
import me.zipi.navitotesla.model.Token
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TeslaAuthApi {
    @POST("/oauth2/v3/token")
    suspend fun refreshAccessToken(@Body request: TeslaRefreshTokenRequest): Response<Token>
}