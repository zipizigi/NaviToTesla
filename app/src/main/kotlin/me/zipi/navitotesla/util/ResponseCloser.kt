package me.zipi.navitotesla.util

import android.util.Log
import retrofit2.Response

object ResponseCloser {
    fun closeAll(response: Response<*>) {
        close(response)
        closeError(response)
    }

    fun close(response: Response<*>) {
        try {
            response.raw().body?.close()
        } catch (ignore: IllegalStateException) {
        } catch (e: Exception) {
            Log.w(ResponseCloser::class.java.name, "Http response close error", e)
            AnalysisUtil.log("Http response close error")
            AnalysisUtil.recordException(e)
        }
    }

    fun closeError(response: Response<*>) {
        try {
            response.errorBody()?.close()
        } catch (e: Exception) {
            Log.w(ResponseCloser::class.java.name, "Http response close error", e)
            AnalysisUtil.log("Http response close error")
            AnalysisUtil.recordException(e)
        }
    }
}
