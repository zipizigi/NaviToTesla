package me.zipi.navitotesla.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

class HttpRetryInterceptor(private val maxRetryCount:Int = 0) : Interceptor {

    private fun sleep(retry: Int, chain: Interceptor.Chain) {
        try {
            var sleep = retry * retry * 100L / 2
            sleep = if (sleep > 3000) 3000 else sleep
            if (sleep > 0) {
                Thread.sleep(sleep)
                AnalysisUtil.log(
                    "retry http request #" + retry + " - " + chain.request().url.toUrl().path
                )
                AnalysisUtil.info(String.format(Locale.getDefault(), "retry sleep... %dms", sleep))
            }
        } catch (ignore: Exception) {
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var response: Response? = null
        var retry = 0
        var isSuccess: Boolean
        while (true) {
            sleep(retry, chain)
            try {
                response = chain.proceed(chain.request())
                isSuccess = response.isSuccessful
                if (!isSuccess) {
                    AnalysisUtil.log("http status code : " + response.code)
                }
            } catch (e: UnknownHostException) {
                AnalysisUtil.info("Network unstable...#" + retry + " " + e.javaClass.name)
                isSuccess = false
            } catch (e: SocketTimeoutException) {
                AnalysisUtil.info("Network unstable...#" + retry + " " + e.javaClass.name)
                isSuccess = false
            }
            if (isSuccess) {
                break
            } else if (response != null && response.code >= 400 && response.code <= 405) {
                AnalysisUtil.info("Http call 4xx error!: " + response.code)
                break
            } else if (retry >= maxRetryCount) {
                if (response == null) {
                    response = chain.proceed(chain.request())
                }
                break
            } else {
                if (response != null) {
                    response.close()
                    response = null
                }
                retry++
            }
        }
        return response!!
    }
}