package me.zipi.navitotesla.service.poifinder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.api.NaverFusionSearchApi
import me.zipi.navitotesla.model.NaverFusionSearch
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.ResponseCloser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * fusion-search 전용 2단 토큰 취득. m.map.naver.com 쿠키를 proof 로 accessToken 을 발급받음.
 * 인증이 빠지면 오류가 아니라 조용히 빈 결과(발급은 201 + 빈 본문, 검색은 200 + totalCount 0)가 돌아옴.
 */
object NaverMobileWebTokenProvider {
    data class Credentials(
        val cookieHeader: String,
        val accessToken: String,
    )

    private const val COOKIE_NAME = "nmap_mobileweb_token"
    private const val COOKIE_ENDPOINT = "https://m.map.naver.com/"
    private const val GRANT_TYPE = "mobile-web"

    // 쿠키 수명 24h. 경계 실패를 피해 절반만 재사용.
    private const val COOKIE_REUSE_MS = 12L * 60L * 60L * 1000L

    // CookieJar 미지정(NO_COOKIES) — 쿠키 자동 저장·전송 없이 Set-Cookie 만 직접 읽음.
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Volatile
    private var cookie: String? = null

    @Volatile
    private var cookieIssuedAt = 0L

    /** accessToken 은 수명이 30초라 캐시하지 않고 호출마다 발급. 실패 시 null. */
    suspend fun credentials(api: NaverFusionSearchApi): Credentials? {
        val proof = cookie() ?: return null
        val cookieHeader = "$COOKIE_NAME=$proof"
        val response =
            try {
                api.issueAccessToken(cookieHeader, NaverFusionSearch.TokenRequest(GRANT_TYPE))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
                return null
            }
        val token = response.body()?.accessToken?.takeIf { it.isNotEmpty() }
        ResponseCloser.closeAll(response)
        if (token == null) {
            AnalysisUtil.warn("naver fusion token issue failed: ${response.code()}")
            cookie = null
            return null
        }
        return Credentials(cookieHeader, token)
    }

    private suspend fun cookie(): String? {
        cookie?.takeIf { System.currentTimeMillis() - cookieIssuedAt < COOKIE_REUSE_MS }?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(COOKIE_ENDPOINT).head().build()).execute().use { res ->
                    val issued =
                        res
                            .headers("Set-Cookie")
                            .firstOrNull { it.startsWith("$COOKIE_NAME=") }
                            ?.substringAfter("=")
                            ?.substringBefore(";")
                            ?.takeIf { it.isNotEmpty() }
                    if (issued == null) {
                        AnalysisUtil.warn("naver mobileweb cookie not issued: ${res.code}")
                    } else {
                        cookie = issued
                        cookieIssuedAt = System.currentTimeMillis()
                    }
                    issued
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
                null
            }
        }
    }
}
