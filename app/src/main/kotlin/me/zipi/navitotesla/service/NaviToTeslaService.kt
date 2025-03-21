package me.zipi.navitotesla.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.perf.metrics.AddTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.R
import me.zipi.navitotesla.exception.DuplicatePoiException
import me.zipi.navitotesla.exception.ForbiddenException
import me.zipi.navitotesla.exception.IgnorePoiException
import me.zipi.navitotesla.exception.NotSupportedNaviException
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.TeslaApiResponse
import me.zipi.navitotesla.model.TeslaRefreshTokenRequest
import me.zipi.navitotesla.model.Token
import me.zipi.navitotesla.model.Vehicle
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.service.share.TeslaShareByApi
import me.zipi.navitotesla.service.share.TeslaShareByApp
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil
import me.zipi.navitotesla.util.PreferencesUtil
import me.zipi.navitotesla.util.ResponseCloser
import retrofit2.Response
import java.io.IOException
import java.util.regex.Pattern

class NaviToTeslaService(
    context: Context,
) {
    private val context: Context
    private val pattern = Pattern.compile("^(?:[가-힣]+\\s[가-힣]+[시군구]|(?:세종시|세종특별시|세종특별자치시)\\s[가-힣\\d]+[읍면동로])\\s")
    private val appRepository: AppRepository

    init {
        this.context = context.applicationContext
        appRepository = AppRepository.getInstance()
    }

    fun isAddress(text: String): Boolean = pattern.matcher(text).find()

    private fun makeToast(text: String) {
        try {
            Log.i(this.javaClass.name, text)
            AnalysisUtil.log(text)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
            e.printStackTrace()
        }
    }

    /**
     * 안내 종료
     */
    fun notificationClear() =
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesUtil.put("lastAddress", "")
        }

    @AddTrace(name = "share")
    suspend fun share(
        packageName: String,
        notificationTitle: String?,
        notificationText: String?,
    ) {
        if (!EnablerUtil.isSendingCheck()) {
            AnalysisUtil.log("skip send share because condition")
            return
        }
        AnalysisUtil.setCustomKey("packageName", packageName)
        AnalysisUtil.setCustomKey("notificationTitle", notificationTitle ?: "")
        AnalysisUtil.setCustomKey("notificationText", notificationText ?: "")
        val eventParam = Bundle()
        eventParam.putString("package", packageName)
        try {
            val poi = getPoi(packageName, notificationTitle, notificationText)
            val lastAddress = PreferencesUtil.getString("lastAddress", "")
            if (lastAddress != poi.getRoadAddress()) {
                try {
                    share(poi)
                } catch (e: ForbiddenException) {
                    AnalysisUtil.log("force expire token and retry...")
                    expireToken()
                    share(poi)
                }
            } else {
                // 마지막 전송 주소와 동일
                // makeToast("목적지 전송 무시\n이전에 전송 요청한 주소와 동일함.");
                AnalysisUtil.logEvent("previous_request_address", eventParam)
            }
            appRepository.clearExpiredPoi()
        } catch (e: DuplicatePoiException) {
            AnalysisUtil.logEvent("duplicated_address", eventParam)
            AnalysisUtil.log("duplicate poi name: " + e.poiName)
            makeToast(context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.duplicatedPoiName))
        } catch (e: NotSupportedNaviException) {
            AnalysisUtil.logEvent("unsupported_navi", eventParam)
            AnalysisUtil.recordException(e)
            makeToast(context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.unsupportedNavi))
        } catch (e: IgnorePoiException) {
            AnalysisUtil.logEvent("ignore_address", eventParam)
        } catch (e: ForbiddenException) {
            makeToast(context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.authFail))
            AnalysisUtil.logEvent("error_share", eventParam)
            AnalysisUtil.recordException(e)
        } catch (e: Exception) {
            Log.e(NaviToTeslaService::class.java.name, "thread inside error", e)
            makeToast(context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.apiError))
            AnalysisUtil.logEvent("error_share", eventParam)
            AnalysisUtil.recordException(e)
            AnalysisUtil.sendUnsentReports()
        }
    }

    @Throws(IOException::class, ForbiddenException::class)
    suspend fun share(poi: Poi) {
        PreferencesUtil.put(
            key = "lastAddress",
            value =
                if (poi.isAddressEmpty()) {
                    ""
                } else {
                    poi.getRoadAddress()
                },
        )
        if (!poi.isAddressEmpty()) {
            makeToast(context.getString(R.string.requestSend) + "\n" + poi.getRoadAddress())
            val shareMode = PreferencesUtil.getString("shareMode", "app")
            if (shareMode == "api" && PreferencesUtil.loadToken() != null) {
                if (refreshToken() == null) {
                    return
                }
                val id = loadVehicleId()
                TeslaShareByApi(context, id).share(poi)
            } else {
                TeslaShareByApp(context).share(poi)
            }
        }
    }

    @AddTrace(name = "getAddress")
    @Throws(
        NotSupportedNaviException::class,
        DuplicatePoiException::class,
        IgnorePoiException::class,
        IOException::class,
    )
    private suspend fun getPoi(
        packageName: String,
        notificationTitle: String?,
        notificationText: String?,
    ): Poi {
        val eventParam = Bundle()
        eventParam.putString("package", packageName)
        val poiFinder = PoiFinderFactory.getPoiFinder(packageName)
        val poiName = poiFinder.parseDestination(notificationText ?: "")
        if (poiName.isEmpty() ||
            poiFinder.isIgnore(
                notificationTitle ?: "",
                notificationText ?: "",
            )
        ) {
            AnalysisUtil.logEvent("address_ignore_or_not_found", eventParam)
            throw IgnorePoiException(packageName)
        }
        val poiAddressEntity = appRepository.getPoiSync(poiName)
        // 10 days cache
        val poi: Poi
        if (poiAddressEntity != null && !poiAddressEntity.isExpire) {
            poi =
                Poi(
                    poiName = poiAddressEntity.poi,
                    roadAddress = poiAddressEntity.address,
                    address = poiAddressEntity.address,
                    longitude = poiAddressEntity.longitude,
                    latitude = poiAddressEntity.latitude,
                )
            AnalysisUtil.logEvent("address_parse_cache", eventParam)
        } else if (isAddress(poiName)) {
            poi =
                Poi(
                    poiName = poiName,
                    roadAddress = poiName,
                    address = poiName,
                    longitude = null,
                    latitude = null,
                )
            AnalysisUtil.logEvent("address_direct", eventParam)
        } else {
            poi = poiFinder.findPoi(poiName)
            AnalysisUtil.logEvent("address_parse_api", eventParam)
            appRepository.savePoi(poi, false)
        }
        return poi
    }

//    val token: Token?
//        get() = PreferencesUtil.loadToken()

    suspend fun getToken(): Token? = PreferencesUtil.loadToken()

    private suspend fun expireToken() {
        PreferencesUtil.expireToken()
    }

    @AddTrace(name = "refreshToken")
    suspend fun refreshToken(): Token? = this.refreshToken(null)

    suspend fun refreshToken(refreshToken: String?): Token? {
        var actualRefreshToken = refreshToken
        if (refreshToken == null) {
            val token = PreferencesUtil.loadToken()
            if (token == null) {
                makeToast(context.getString(R.string.notExistsToken))
                return null
            } else if (!token.isExpire()) {
                return token
            } else {
                actualRefreshToken = token.refreshToken
            }
        }
        AnalysisUtil.log("Start refresh access token")
        var token: Token? = null
        try {
            val newToken: Response<Token> = appRepository.teslaAuthApi.refreshAccessToken(TeslaRefreshTokenRequest(actualRefreshToken!!))
            if (newToken.isSuccessful && newToken.body() != null) {
                PreferencesUtil.saveToken(newToken.body()!!)
                token = newToken.body()
                AnalysisUtil.log("Success refresh access token")
            }
            ResponseCloser.closeAll(newToken)
        } catch (e: Exception) {
            Log.w(this.javaClass.name, "refresh token fail", e)
            makeToast(context.getString(R.string.refreshTokenError))
            AnalysisUtil.recordException(e)
        }
        if (token == null) {
            AnalysisUtil.log("fail refresh access token. token is null")
        }
        return token
    }

    @AddTrace(name = "getVehicles")
    suspend fun getVehicles(token: Token?): List<Vehicle> {
        var actualToken = token
        if (token == null) {
            actualToken = PreferencesUtil.loadToken()
        }
        if (actualToken?.refreshToken == null) {
            makeToast(context.getString(R.string.requireToken))
            return ArrayList()
        }
        val vehicles = mutableListOf<Vehicle>()
        try {
            if (refreshToken() == null) {
                return vehicles
            }
            val response: Response<TeslaApiResponse.ListType<Map<String, Any>>> = appRepository.teslaApi.products()
            if (response.code() == 401) {
                makeToast(context.getString(R.string.invalidToken))
            } else if (response.isSuccessful && response.body() != null) {
                response
                    .body()!!
                    .response
                    .filter { it.containsKey("vin") && it.containsKey("vehicle_id") }
                    .map {
                        Vehicle(
                            id = (it["id"] as Number).toLong(),
                            vehicleId = (it["vehicle_id"] as Number).toLong(),
                            displayName = it["display_name"].toString(),
                            state = it["state"].toString(),
                        )
                    }.apply { vehicles.addAll(this) }
            } else {
                Log.w(this.javaClass.name, "get vehicle error: $response")
            }
        } catch (e: Exception) {
            Log.w(this.javaClass.name, "get vehicle error", e)
            AnalysisUtil.recordException(e)
        }
        if (vehicles.isEmpty()) {
            makeToast(context.getString(R.string.noVehicle))
        }
        return vehicles
    }

    fun saveVehicleId(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesUtil.put("vehicleId", id)
        }
    }

    suspend fun loadVehicleId(): Long = PreferencesUtil.getLong("vehicleId", 0L)

    suspend fun clearPoiCache() {
        appRepository.clearAllPoi()
    }
}
