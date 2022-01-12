package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TeslaRefreshTokenRequest {
    @SerializedName("grant_type")
    String grantType;
    @SerializedName("client_id")
    String clientId;
    @SerializedName("refresh_token")
    String refreshToken;
    @SerializedName("scope")
    String scope;
    public TeslaRefreshTokenRequest(String refreshToken) {
        this.grantType = "refresh_token";
        this.scope = "openid email offline_access";
        this.refreshToken = refreshToken;
        this.clientId = "ownerapi";
    }

}
