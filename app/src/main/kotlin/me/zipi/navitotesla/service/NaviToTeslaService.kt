package me.zipi.navitotesla.service

import android.content.Context
import android.os.Bundle
import com.google.firebase.perf.metrics.AddTrace
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.R
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.exception.DuplicatePoiException
import me.zipi.navitotesla.exception.ForbiddenException
import me.zipi.navitotesla.exception.IgnorePoiException
import me.zipi.navitotesla.exception.NotSupportedNaviException
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.model.SendSettings
import me.zipi.navitotesla.model.ShareTransport
import me.zipi.navitotesla.model.TeslaApiResponse
import me.zipi.navitotesla.model.TeslaRefreshTokenRequest
import me.zipi.navitotesla.model.Token
import me.zipi.navitotesla.model.Vehicle
import me.zipi.navitotesla.service.place.DestinationAddressResolver
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory
import me.zipi.navitotesla.service.share.SendPlanner
import me.zipi.navitotesla.service.share.TeslaShareByApi
import me.zipi.navitotesla.service.share.TeslaShareByApp
import me.zipi.navitotesla.ui.poi.PoiSelectionOverlay
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil
import me.zipi.navitotesla.util.PreferencesUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import me.zipi.navitotesla.util.ResponseCloser
import retrofit2.Response
import java.io.IOException
import java.util.Locale
import java.util.regex.Pattern

class NaviToTeslaService(
    context: Context,
) {
    private val context = context.applicationContext
    private val appRepository = AppRepository.getInstance()

    fun isAddress(text: String): Boolean = ADDRESS_PATTERN.matcher(text).find()

    private fun makeToast(
        text: String,
        level: AnalysisUtil.ToastLevel = AnalysisUtil.ToastLevel.INFO,
    ) = AnalysisUtil.makeToast(context = context, text = text, level = level)

    /**
     * 안내 종료
     */
    suspend fun notificationClear() {
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
        val eventParam = Bundle()
        eventParam.putString("package", packageName)
        try {
            appRepository.clearExpiredPoi()
            val poi = getPoi(packageName, notificationTitle, notificationText)
            val lastAddress = PreferencesUtil.getString("lastAddress", "")
            if (lastAddress != poi.getRoadAddress()) {
                try {
                    share(poi)
                } catch (_: ForbiddenException) {
                    AnalysisUtil.warn("force expire token and retry...")
                    expireToken()
                    share(poi)
                }
            } else {
                val deltaMs = if (lastShareAt > 0L) System.currentTimeMillis() - lastShareAt else -1L
                AnalysisUtil.log(
                    "skip share: duplicate dest, pkg=$packageName, addr=${poi.getRoadAddress()}, deltaMs=$deltaMs",
                )
                AnalysisUtil.logEvent("previous_request_address", eventParam)
            }
        } catch (e: DuplicatePoiException) {
            AnalysisUtil.logEvent("duplicated_address", eventParam)
            AnalysisUtil.log("duplicate poi name: " + e.poiName)
            val duplicatedToast = {
                makeToast(
                    text = context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.duplicatedPoiName),
                    level = AnalysisUtil.ToastLevel.WARN,
                )
            }
            val selectionEnabled = PreferencesUtil.getBoolean("duplicatePoiSelection", true)
            if (selectionEnabled && e.candidates.isNotEmpty()) {
                val shown =
                    PoiSelectionOverlay.show(context, e.candidates) {
                        AnalysisUtil.log("duplicate poi selection dismissed: " + e.poiName)
                        duplicatedToast()
                    }
                if (!shown) {
                    AnalysisUtil.warn("overlay permission denied for: " + e.poiName)
                    duplicatedToast()
                }
            } else {
                duplicatedToast()
            }
        } catch (e: NotSupportedNaviException) {
            AnalysisUtil.logEvent("unsupported_navi", eventParam)
            AnalysisUtil.recordException(e)
            makeToast(
                text = context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.unsupportedNavi),
                level = AnalysisUtil.ToastLevel.WARN,
            )
        } catch (_: IgnorePoiException) {
            AnalysisUtil.logEvent("ignore_address", eventParam)
        } catch (e: ForbiddenException) {
            makeToast(
                text = context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.authFail),
                level = AnalysisUtil.ToastLevel.WARN,
            )
            AnalysisUtil.logEvent("error_share", eventParam)
            AnalysisUtil.recordException(e)
        } catch (e: Exception) {
            AnalysisUtil.error("thread inside error", e)
            makeToast(
                text = context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.apiError),
                level = AnalysisUtil.ToastLevel.ERROR,
            )
            AnalysisUtil.logEvent("error_share", eventParam)
            AnalysisUtil.recordException(e)
            AnalysisUtil.sendUnsentReports()
        }
    }

    @Throws(IOException::class, ForbiddenException::class)
    suspend fun share(poi: Poi) {
        if (poi.isAddressEmpty()) {
            AnalysisUtil.warn("share skipped: empty poi name=${poi.poiName}, pkg=${poi.packageName}")
            PreferencesUtil.put(key = "lastAddress", value = "")
            makeToast(
                text = context.getString(R.string.sendDestinationFail) + "\n" + context.getString(R.string.addressNotFound),
                level = AnalysisUtil.ToastLevel.WARN,
            )
            return
        }

        // favorite/cache/좌표 무관 — classify 와 plan 이 각자 책임 안에서 처리.
        val searchability = DestinationAddressResolver.classify(poi)

        val shareMode = PreferencesUtil.getString("shareMode", "app")
        val transport =
            if (shareMode == "api" && PreferencesUtil.loadToken() != null) {
                ShareTransport.API
            } else {
                ShareTransport.APP
            }
        val settings =
            SendSettings(
                defaultMode = PreferencesUtil.getDefaultSendMode(),
                fallbackMode = PreferencesUtil.getFallbackSendMode(),
                treatUnknownAsNotSearchable = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_SEND_UNKNOWN_AS_NOT_SEARCHABLE),
                shareTransport = transport,
                locale = Locale.getDefault(),
            )

        val payload =
            SendPlanner.plan(
                poi = poi,
                searchability = searchability,
                isDuplicateSelected = poi.isDuplicate,
                settings = settings,
            )

        PreferencesUtil.put(key = "lastAddress", value = poi.getRoadAddress())

        lastShareAt = System.currentTimeMillis()
        makeToast(context.getString(R.string.requestSend) + "\n" + payload.displayText)
        if (transport == ShareTransport.API) {
            if (refreshToken() == null) {
                return
            }
            val id = loadVehicleId()
            TeslaShareByApi(context, id, poi.packageName).share(payload)
        } else {
            TeslaShareByApp(context, poi.packageName).share(payload)
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
        val poiAddressEntity = appRepository.getPoiSync(poiName, packageName)
        val poi: Poi
        if (poiAddressEntity != null && !poiAddressEntity.isExpire) {
            // favorite 의 원래 packageName 보존 — cross-package favorite 의 cache key 일관성 유지.
            // 빈 packageName(글로벌 favorite) 인 경우만 share 출처 packageName 으로 보정.
            val effectivePackage = poiAddressEntity.packageName?.takeIf { it.isNotEmpty() } ?: packageName
            poi = poiAddressEntity.toPoi().copy(packageName = effectivePackage)
            AnalysisUtil.logEvent("address_parse_cache", eventParam)
        } else if (isAddress(poiName)) {
            poi =
                Poi(
                    poiName = poiName,
                    roadAddress = poiName,
                    address = null,
                    longitude = null,
                    latitude = null,
                    packageName = packageName,
                )
            appRepository.savePoi(poi, false)
            AnalysisUtil.logEvent("address_direct", eventParam)
        } else {
            poi = poiFinder.findPoi(poiName, packageName)
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
                makeToast(context.getString(R.string.notExistsToken), AnalysisUtil.ToastLevel.WARN)
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
            val newToken: Response<Token> =
                appRepository.teslaAuthApi.refreshAccessToken(
                    TeslaRefreshTokenRequest(actualRefreshToken),
                )
            if (newToken.isSuccessful) {
                newToken.body()?.let {
                    PreferencesUtil.saveToken(it)
                    token = it
                    AnalysisUtil.log("Success refresh access token")
                }
            }
            ResponseCloser.closeAll(newToken)
        } catch (e: Exception) {
            AnalysisUtil.warn("refresh token fail", e)
            makeToast(context.getString(R.string.refreshTokenError), AnalysisUtil.ToastLevel.WARN)
            AnalysisUtil.recordException(e)
        }
        if (token == null) {
            AnalysisUtil.warn("fail refresh access token. token is null")
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
            makeToast(context.getString(R.string.requireToken), AnalysisUtil.ToastLevel.WARN)
            return emptyList()
        }
        val vehicles = mutableListOf<Vehicle>()
        try {
            if (refreshToken() == null) {
                return vehicles
            }
            val response: Response<TeslaApiResponse.ListType<Map<String, Any>>> = appRepository.teslaApi.products()
            if (response.code() == 401) {
                makeToast(context.getString(R.string.invalidToken), AnalysisUtil.ToastLevel.WARN)
            } else if (response.isSuccessful) {
                response
                    .body()
                    ?.response
                    ?.filter { it.containsKey("vin") && it.containsKey("vehicle_id") }
                    ?.map {
                        Vehicle(
                            id = (it["id"] as Number).toLong(),
                            vehicleId = (it["vehicle_id"] as Number).toLong(),
                            displayName = it["display_name"].toString(),
                            state = it["state"].toString(),
                        )
                    }?.let { vehicles.addAll(it) }
            } else {
                AnalysisUtil.warn("get vehicle error: $response")
            }
        } catch (e: Exception) {
            AnalysisUtil.warn("get vehicle error", e)
            AnalysisUtil.recordException(e)
        }
        if (vehicles.isEmpty()) {
            makeToast(text = context.getString(R.string.noVehicle), level = AnalysisUtil.ToastLevel.WARN)
        }
        return vehicles
    }

    suspend fun saveVehicleId(id: Long) {
        PreferencesUtil.put("vehicleId", id)
    }

    suspend fun loadVehicleId(): Long = PreferencesUtil.getLong("vehicleId", 0L)

    companion object {
        private val ADDRESS_PATTERN = Pattern.compile("^(?:[가-힣]+\\s[가-힣]+[시군구]|(?:세종시|세종특별시|세종특별자치시)\\s[가-힣\\d]+[읍면동로])\\s")

        @Volatile
        private var lastShareAt: Long = 0L
    }
}
