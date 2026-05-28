package me.zipi.navitotesla.service.share

import me.zipi.navitotesla.model.SendPayload
import java.io.IOException

interface TeslaShare {
    @Throws(IOException::class)
    suspend fun share(payload: SendPayload)
}
