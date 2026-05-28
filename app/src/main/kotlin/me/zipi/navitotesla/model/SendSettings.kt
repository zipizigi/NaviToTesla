package me.zipi.navitotesla.model

import java.util.Locale

data class SendSettings(
    val defaultMode: SendMode,
    val fallbackMode: SendMode,
    val treatUnknownAsNotSearchable: Boolean,
    val shareTransport: ShareTransport,
    val locale: Locale,
)

enum class ShareTransport { APP, API }
