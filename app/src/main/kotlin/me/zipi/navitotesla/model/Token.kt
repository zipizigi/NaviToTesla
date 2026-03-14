package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName
import kotlin.math.abs

class Token(
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("access_token")
    var accessToken: String,
    @SerializedName("expires_in")
    var expires: Int = 0,
    var updated: Long = 0,
) {
    fun isExpire(): Boolean = abs(updated - System.currentTimeMillis()) / 1000L / 60L / 60L > 7 // expire in 8 hours

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (refreshToken != (other as Token).refreshToken) return false

        return true
    }

    override fun hashCode(): Int = refreshToken.hashCode()
}
