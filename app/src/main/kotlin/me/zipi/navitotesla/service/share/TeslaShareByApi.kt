package me.zipi.navitotesla.service.share

import android.content.Context
import android.os.Bundle
import android.util.Log
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.exception.ForbiddenException
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.ShareRequest
import me.zipi.navitotesla.model.TeslaApiResponse
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.ResponseCloser
import retrofit2.Response
import java.io.IOException

class TeslaShareByApi(context: Context, private val vehicleId: Long) : TeslaShareBase(context), TeslaShare {
    @Throws(IOException::class)
    override suspend fun share(poi: Poi) {
        val address = poi.getRoadAddress()
        if (BuildConfig.DEBUG) {
            AnalysisUtil.makeToast(context, "[DEBUG] 목적지 전송 By api Skip\n$address")
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }
            return
        }
        AnalysisUtil.log("share using tesla api share")
        val appRepository = AppRepository.getInstance()

        val response: Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> =
            appRepository.teslaApi.share(vehicleId, ShareRequest(address))

        var result: TeslaApiResponse.ObjectType<TeslaApiResponse.Result>? = null
        if (response.isSuccessful) {
            result = response.body()
        }
        if (result != null && result.error == null && (result.response?.result == true)) {
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationSuccess) + "\n" + address)
            AnalysisUtil.log("send_success")
            AnalysisUtil.logEvent("share_by_api_success", Bundle())
        } else {
            Log.w(NaviToTeslaService::class.java.name, response.toString())
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationFail) + (result?.errorDescription ?: ""))

            AnalysisUtil.log("send_fail")
            AnalysisUtil.setCustomKey("address", address)
            if (result?.errorDescription != null) {
                AnalysisUtil.log("errorDescription: " + result.errorDescription)
            }
            if (!response.isSuccessful) {
                AnalysisUtil.log("Http response code: " + response.code())
                if (response.errorBody() != null) {
                    AnalysisUtil.log("Http error response: " + response.errorBody()!!.string())
                }
            }
            val exception: RuntimeException
            if (response.code() == 401) {
                var errorString = ""
                if (response.errorBody() != null) {
                    errorString = response.errorBody()!!.string()
                }
                exception = ForbiddenException(401, errorString)
            } else {
                exception = RuntimeException("Send address fail")
            }
            AnalysisUtil.logEvent("share_by_api_fail", Bundle())
            AnalysisUtil.recordException(exception)
            throw exception
        }
        ResponseCloser.closeAll(response)
    }
}
