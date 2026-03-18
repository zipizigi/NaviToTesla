package me.zipi.navitotesla.service.share

import android.content.Context
import android.os.Bundle
import me.zipi.navitotesla.model.Poi

abstract class TeslaShareBase(
    protected val context: Context,
) : TeslaShare {
    protected fun eventParam(poi: Poi): Bundle = Bundle().apply { putString("package", poi.packageName) }
}
