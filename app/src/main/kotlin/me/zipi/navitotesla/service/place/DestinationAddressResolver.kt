package me.zipi.navitotesla.service.place

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import kotlin.random.Random

object DestinationAddressResolver {
    @Volatile
    var matcher: PlaceAutocompleteMatcher = RealPlaceAutocompleteMatcher()

    @Volatile
    var cacheClient: PlaceAutocompleteCacheClient = FirestorePlaceAutocompleteCacheClient

    suspend fun classify(poi: Poi): Searchability {
        val poiName = poi.poiName ?: return Searchability.Unknown
        val roadAddress = poi.getRoadAddress()
        val eventParam = Bundle().apply { putString("package", poi.packageName) }
        AnalysisUtil.debug("classify start: poi='$poiName', pkg=${poi.packageName}")

        val dao = AppDatabase.getInstance().poiAddressDao()
        val cached = dao.findPoiByPackage(poiName, poi.packageName)?.takeUnless { it.isExpire }
        if (cached != null) {
            AppRepository.getInstance().touchLastUsed(poi)
        }
        when (cached?.searchability) {
            Searchability.Searchable -> {
                AnalysisUtil.debug("classify: local cache hit, searchable")
                return Searchability.Searchable
            }

            Searchability.NotSearchable -> {
                AnalysisUtil.debug("classify: local cache hit, not_searchable")
                return Searchability.NotSearchable
            }

            Searchability.Unknown -> {
                if (isWithinCooldown(cached.lastCheckedAt)) {
                    AnalysisUtil.debug("classify: cooldown active (lastCheckedAt=${cached.lastCheckedAt}), unknown")
                    AnalysisUtil.logEvent("place_check_cooldown_skip", eventParam)
                    return Searchability.Unknown
                }
            }

            null -> {
            }
        }

        val firebaseDisabledFlavor = !BuildConfig.DEBUG && BuildConfig.BUILD_MODE != "playstore"
        if (firebaseDisabledFlavor) {
            AnalysisUtil.debug("classify: firebase-disabled flavor, unknown")
            return Searchability.Unknown
        }

        val lookupEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
        if (!lookupEnabled) {
            AnalysisUtil.debug("classify: lookup disabled by RC, unknown")
            return Searchability.Unknown
        }

        AnalysisUtil.logEvent("firestore_get", eventParam)
        when (cacheClient.lookup(roadAddress)) {
            PlaceAutocompleteCacheEntry.Searchable -> {
                AnalysisUtil.debug("classify: firestore hit=searchable")
                AnalysisUtil.logEvent(
                    "firestore_hit",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "searchable")
                    },
                )
                AppRepository.getInstance().markClassified(poi, Searchability.Searchable)
                return Searchability.Searchable
            }

            PlaceAutocompleteCacheEntry.NotSearchable -> {
                AnalysisUtil.debug("classify: firestore hit=not_searchable")
                AnalysisUtil.logEvent(
                    "firestore_hit",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "not_searchable")
                    },
                )
                AppRepository.getInstance().markClassified(poi, Searchability.NotSearchable)
                return Searchability.NotSearchable
            }

            null -> {
                AnalysisUtil.debug("classify: firestore miss")
            }
        }

        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
            AnalysisUtil.debug("classify: places update disabled by RC, unknown")
            AppRepository.getInstance().markClassified(poi, Searchability.Unknown)
            return Searchability.Unknown
        }

        val ratio = RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO).coerceIn(0L, 100L).toInt()
        if (Random.nextInt(100) >= ratio) {
            AnalysisUtil.debug("classify: places update sampled out (ratio=$ratio), unknown")
            AppRepository.getInstance().markClassified(poi, Searchability.Unknown)
            return Searchability.Unknown
        }

        val matched =
            try {
                AnalysisUtil.logEvent("places_api_call", eventParam)
                matcher.isMatch(roadAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
                null
            }
        AnalysisUtil.debug("classify: places_api match=$matched")

        return when (matched) {
            true -> {
                AnalysisUtil.logEvent(
                    "firestore_set",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "searchable")
                    },
                )
                cacheClient.cache(roadAddress, searchable = true)
                AppRepository.getInstance().markClassified(poi, Searchability.Searchable)
                Searchability.Searchable
            }

            false -> {
                AnalysisUtil.logEvent(
                    "firestore_set",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "not_searchable")
                    },
                )
                cacheClient.cache(roadAddress, searchable = false)
                AppRepository.getInstance().markClassified(poi, Searchability.NotSearchable)
                Searchability.NotSearchable
            }

            null -> {
                AppRepository.getInstance().markClassified(poi, Searchability.Unknown)
                Searchability.Unknown
            }
        }
    }

    private fun isWithinCooldown(lastCheckedAt: Long?): Boolean {
        if (lastCheckedAt == null) return false
        val cooldownHours =
            RemoteConfigUtil
                .getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_COOLDOWN_HOURS)
                .coerceIn(0L, MAX_COOLDOWN_HOURS)
        if (cooldownHours <= 0L) return false
        val cooldownMs = cooldownHours * 60L * 60L * 1000L
        val elapsedMs = System.currentTimeMillis() - lastCheckedAt
        // 시계 역행(elapsedMs < 0) 시 쿨다운 만료로 처리.
        if (elapsedMs < 0L) return false
        return elapsedMs < cooldownMs
    }

    // cooldownHours * 3_600_000L Long overflow 가드 상한.
    private const val MAX_COOLDOWN_HOURS = Long.MAX_VALUE / (60L * 60L * 1000L)
}
