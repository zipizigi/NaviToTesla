package me.zipi.navitotesla.model

data class SendPayload(
    val sendText: String,
    val displayText: String,
    val mode: SendMode,
    val viaUrl: Boolean,
)
