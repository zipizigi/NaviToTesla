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
        val cached = dao.findPoiByPackage(poiName, poi.packageName)
        if (cached != null && !cached.isExpire && cached.searchable != null) {
            AnalysisUtil.debug("classify: local cache hit, searchable=${cached.searchable}")
            return if (cached.searchable) Searchability.Searchable else Searchability.NotSearchable
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
                AppRepository.getInstance().markClassified(poi, true)
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
                AppRepository.getInstance().markClassified(poi, false)
                return Searchability.NotSearchable
            }

            null -> {
                AnalysisUtil.debug("classify: firestore miss")
            }
        }

        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
            AnalysisUtil.debug("classify: places update disabled by RC, unknown")
            return Searchability.Unknown
        }

        val ratio = RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO).coerceIn(0L, 100L).toInt()
        if (Random.nextInt(100) >= ratio) {
            AnalysisUtil.debug("classify: places update sampled out (ratio=$ratio), unknown")
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
                AppRepository.getInstance().markClassified(poi, true)
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
                AppRepository.getInstance().markClassified(poi, false)
                Searchability.NotSearchable
            }

            null -> {
                Searchability.Unknown
            }
        }
    }
}
