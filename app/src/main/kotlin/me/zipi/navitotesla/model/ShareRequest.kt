package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

data class ShareRequest(
    val address: String,
) {
    val type = "share_ext_content_raw"
    val locale = "ko-KR"

    @SerializedName("timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis() / 1000L
    val value: Value = Value(address)

    data class Value(
        @SerializedName("android.intent.extra.TEXT")
        val text: String,
    )
}
/**
 * POST https://owner-api.teslamotors.com/api/1/vehicles/1493132499069707/command/share
 *
 * {
 *     "locale": "ko-KR",
 *     "timestamp": 1704673330414,
 *     "type": "share_ext_content_raw",
 *     "value": {
 *         "android.intent.ACTION": "android.intent.action.SEND",
 *         "android.intent.TYPE": "text/plain",
 *         "android.intent.extra.TEXT": "37.3284973,127.112211"
 *     }
 * }
 */
