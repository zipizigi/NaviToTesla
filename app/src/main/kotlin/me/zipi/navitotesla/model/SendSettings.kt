package me.zipi.navitotesla.model

data class SendSettings(
    val defaultMode: SendMode,
    val fallbackMode: SendMode,
    val treatUnknownAsNotSearchable: Boolean,
)
