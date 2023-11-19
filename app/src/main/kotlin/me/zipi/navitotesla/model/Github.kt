package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName


@Suppress("unused")
class Github {
    data class Release(
        var id: Long? = null,

        @SerializedName("tag_name")
        var tagName: String? = null,
        var name: String? = null,
        var assets: List<Asset>? = null,
        var body: String? = null,
        @SerializedName("prerelease")
        var isPreRelease: Boolean? = null,
    )


    data class Asset(
        var id: Long? = null,
        var name: String? = null,

        @SerializedName("content_type")
        var contentType: String? = null,

        @SerializedName("browser_download_url")
        var downloadUrl: String? = null,
    )
}