package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.model.Poi
import java.io.IOException

interface TeslaShare {
    @Throws(IOException::class)
    suspend fun share(poi: Poi)
}
