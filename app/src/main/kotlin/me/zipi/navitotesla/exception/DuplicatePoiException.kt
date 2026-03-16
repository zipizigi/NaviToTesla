package me.zipi.navitotesla.exception

import me.zipi.navitotesla.model.Poi

class DuplicatePoiException(
    val poiName: String,
    val candidates: List<Poi> = emptyList(),
) : RuntimeException()
