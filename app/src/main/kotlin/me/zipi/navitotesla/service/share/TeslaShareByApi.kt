package me.zipi.navitotesla.service.share

import android.content.Context
import kotlinx.coroutines.delay
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.exception.ForbiddenException
import me.zipi.navitotesla.model.SendPayload
import me.zipi.navitotesla.model.ShareRequest
import me.zipi.navitotesla.model.TeslaApiResponse
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.ResponseCloser
import retrofit2.Response
import java.io.IOException

class TeslaShareByApi(
    context: Context,
    private val vehicleId: Long,
    private val packageName: String?,
) : TeslaShareBase(context),
    TeslaShare {
    @Throws(IOException::class)
    override suspend fun share(payload: SendPayload) {
        if (BuildConfig.DEBUG) {
            AnalysisUtil.makeToast(context, "[DEBUG] 목적지 전송 By api Skip\n${payload.displayText}")
            delay(500)
            return
        }
        AnalysisUtil.log("share using tesla api share")
        val appRepository = AppRepository.getInstance()

        val response: Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> =
            appRepository.teslaApi.share(vehicleId, ShareRequest(payload.sendText))

        val result = response.body().takeIf { response.isSuccessful }
        if (result != null && result.error == null && (result.response?.result == true)) {
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationSuccess) + "\n" + payload.displayText)
            AnalysisUtil.log("send_success")
            AnalysisUtil.logEvent("share_by_api_success", eventParam(packageName, payload))
        } else {
            AnalysisUtil.warn(response.toString())
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationFail) + (result?.errorDescription ?: ""))

            AnalysisUtil.log("send_fail")
            if (result?.errorDescription != null) {
                AnalysisUtil.log("errorDescription: " + result.errorDescription)
            }
            if (!response.isSuccessful) {
                AnalysisUtil.log("Http response code: " + response.code())
                response.errorBody()?.string()?.let { AnalysisUtil.log("Http error response: $it") }
            }
            val exception: RuntimeException =
                if (response.code() == 401) {
                    ForbiddenException(401, response.errorBody()?.string() ?: "")
                } else {
                    RuntimeException("Send address fail")
                }
            AnalysisUtil.logEvent("share_by_api_fail", eventParam(packageName, payload))
            AnalysisUtil.recordException(exception)
            throw exception
        }
        ResponseCloser.closeAll(response)
    }
}
