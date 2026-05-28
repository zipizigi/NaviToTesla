package me.zipi.navitotesla.service.share

import android.content.Context
import android.os.Bundle
import me.zipi.navitotesla.model.SendPayload

abstract class TeslaShareBase(
    protected val context: Context,
) : TeslaShare {
    protected fun eventParam(
        packageName: String?,
        payload: SendPayload,
    ): Bundle =
        Bundle().apply {
            putString("package", packageName)
            putString("mode", payload.mode.name)
            putString("via_url", payload.viaUrl.toString())
        }
}
