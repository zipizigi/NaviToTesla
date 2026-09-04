package me.zipi.navitotesla.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

class HttpRetryInterceptor(
    private val maxRetryCount: Int = 0,
) : Interceptor {
    private companion object {
        const val MAX_SLEEP_MS = 2_000L

        // 시간이 지나면 풀리는 4xx. 나머지 4xx 는 재시도로 결과가 바뀌지 않음.
        val RETRYABLE_4XX = setOf(408, 425, 429)
    }

    private fun sleep(
        retry: Int,
        chain: Interceptor.Chain,
    ) {
        try {
            val sleep = if (retry <= 0) 0L else (300L shl (retry - 1)).coerceAtMost(MAX_SLEEP_MS)
            if (sleep > 0) {
                Thread.sleep(sleep)
                AnalysisUtil.log(
                    "retry http request #" + retry + " - " + chain.request().url.toUrl().path,
                )
                AnalysisUtil.info(String.format(Locale.getDefault(), "retry sleep... %dms", sleep))
            }
        } catch (_: Exception) {
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
                    AnalysisUtil.warn("http status code : " + response.code)
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
            } else if (response != null && response.code in 400..499 && response.code !in RETRYABLE_4XX) {
                AnalysisUtil.warn("Http call 4xx error!: " + response.code)
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
