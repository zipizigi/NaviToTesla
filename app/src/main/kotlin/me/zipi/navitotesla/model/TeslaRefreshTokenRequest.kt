package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

data class TeslaRefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String,
) {
    @SerializedName("grant_type")
    val grantType = "refresh_token"

    @SerializedName("client_id")
    val clientId = "ownerapi"

    @SerializedName("scope")
    val scope = "openid email offline_access"
}
