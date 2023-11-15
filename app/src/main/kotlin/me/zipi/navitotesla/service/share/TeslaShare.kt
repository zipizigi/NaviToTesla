package me.zipi.navitotesla.service.share

import java.io.IOException

interface TeslaShare {
    @Throws(IOException::class)
    fun share(address: String)
}