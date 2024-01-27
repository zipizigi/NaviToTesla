package me.zipi.navitotesla.api

import me.zipi.navitotesla.model.ShareRequest
import me.zipi.navitotesla.model.TeslaApiResponse
import me.zipi.navitotesla.model.Vehicle
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TeslaApi {
    @GET("/api/1/vehicles")
    @Deprecated("endpoint is only available on fleetapi")
    suspend fun vehicles(): Response<TeslaApiResponse.ListType<Vehicle>>

    @GET("/api/1/products")
    suspend fun products(): Response<TeslaApiResponse.ListType<Map<String, Any>>>

    @POST("/api/1/vehicles/{id}/command/share")
    suspend fun share(
        @Path("id") id: Long,
        @Body request: ShareRequest,
    ): Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>>
}
