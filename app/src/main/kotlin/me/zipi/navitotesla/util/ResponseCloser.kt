package me.zipi.navitotesla.util

import retrofit2.Response

object ResponseCloser {
    fun closeAll(response: Response<*>) {
        close(response)
        closeError(response)
    }

    fun close(response: Response<*>) {
        try {
            response.raw().body.close()
        } catch (_: IllegalStateException) {
        } catch (e: Exception) {
            AnalysisUtil.warn("Http response close error", e)
            AnalysisUtil.recordException(e)
        }
    }

    fun closeError(response: Response<*>) {
        try {
            response.errorBody()?.close()
        } catch (e: Exception) {
            AnalysisUtil.warn("Http response close error", e)
            AnalysisUtil.recordException(e)
        }
    }
}
