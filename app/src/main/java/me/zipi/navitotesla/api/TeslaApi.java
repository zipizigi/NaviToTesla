package me.zipi.navitotesla.api;

import me.zipi.navitotesla.model.ShareRequest;
import me.zipi.navitotesla.model.TeslaApiResponse;
import me.zipi.navitotesla.model.Vehicle;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface TeslaApi {

    @GET("/api/1/vehicles")
    Call<TeslaApiResponse.ListType<Vehicle>> vehicles();

    @POST("/api/1/vehicles/{id}/command/share")
    Call<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> share(@Path("id") Long id, @Body ShareRequest request);
}
