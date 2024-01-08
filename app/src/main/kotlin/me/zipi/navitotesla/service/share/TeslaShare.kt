package me.zipi.navitotesla.service.share

import java.io.IOException

interface TeslaShare {
    @Throws(IOException::class)
    suspend fun share(address: String)
}