package me.zipi.navitotesla.api;

import me.zipi.navitotesla.model.TeslaRefreshTokenRequest;
import me.zipi.navitotesla.model.Token;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TeslaAuthApi {

    @POST("/oauth2/v3/token")
    Call<Token> refreshAccessToken(@Body TeslaRefreshTokenRequest request);

}
