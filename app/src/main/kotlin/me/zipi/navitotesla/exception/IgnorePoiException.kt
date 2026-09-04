package me.zipi.navitotesla.exception

import me.zipi.navitotesla.service.poifinder.IgnoreReason

class IgnorePoiException(
    packageName: String,
    val reason: IgnoreReason,
) : RuntimeException("$packageName: $reason")
