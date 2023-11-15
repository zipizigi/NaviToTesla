package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName


data class ShareRequest(val address: String) {
    var type = "share_ext_content_raw"
    var locale = "ko-KR"

    @SerializedName("timestamp_ms")
    var timestampMs: Long = System.currentTimeMillis() / 1000L
    var value: Value = Value(address)

    data class Value(
        @SerializedName("android.intent.extra.TEXT")
        var text: String? = null
    )
}
