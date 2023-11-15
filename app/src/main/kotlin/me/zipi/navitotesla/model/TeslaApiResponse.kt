package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

class TeslaApiResponse {
    data class ListType<T>(
        var response: List<T> = listOf(),
        var count: Int = 0,
        var error: String? = null,

        @SerializedName("error_description")
        var errorDescription: String? = null,
    )


    class ObjectType<T>(
        var response: T?,
        var count: Int = 0,
        var error: String? = null,

        @SerializedName("error_description")
        var errorDescription: String? = null,
    )

    data class Result(
        var result: Boolean?,
        var queued: Boolean? = null,
    )
}