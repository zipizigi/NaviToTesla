package me.zipi.navitotesla.service.place

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil

object DestinationAddressResolver {
    @Volatile
    var matcher: PlaceAutocompleteMatcher = RealPlaceAutocompleteMatcher()

    @Volatile
    var cacheClient: PlaceAutocompleteCacheClient = FirestorePlaceAutocompleteCacheClient

    suspend fun resolve(poi: Poi): String {
        val poiName = poi.poiName ?: return poi.getRoadAddress()
        val roadAddress = poi.getRoadAddress()
        val jibunAddress = poi.getAddress()
        val eventParam = Bundle().apply { putString("package", poi.packageName) }
        AnalysisUtil.debug("resolve start: poi='$poiName', pkg=${poi.packageName}")

        val dao = AppDatabase.getInstance().poiAddressDao()
        val cached = dao.findPoiByPackage(poiName, poi.packageName)
        if (cached != null && !cached.isExpire) {
            cached.sentAddress?.let {
                AnalysisUtil.debug("resolve: local cache hit, sentMode=${cached.sentMode}")
                return it
            }
        }

        val firebaseDisabledFlavor = !BuildConfig.DEBUG && BuildConfig.BUILD_MODE != "playstore"
        if (firebaseDisabledFlavor || jibunAddress.isEmpty() || jibunAddress == roadAddress) {
            val reason =
                when {
                    firebaseDisabledFlavor -> "flavor"
                    jibunAddress.isEmpty() -> "no_jibun"
                    else -> "jibun=road"
                }
            AnalysisUtil.debug("resolve: skip firestore/places ($reason), using road")
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            return roadAddress
        }

        val lookupEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
        if (!lookupEnabled) {
            AnalysisUtil.debug("resolve: lookup disabled by RC, using road")
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            return roadAddress
        }

        AnalysisUtil.logEvent("firestore_get", eventParam)
        when (cacheClient.lookup(roadAddress)) {
            PlaceAutocompleteCacheEntry.Searchable -> {
                AnalysisUtil.debug("resolve: firestore hit=searchable, using road")
                AnalysisUtil.logEvent(
                    "firestore_hit",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "searchable")
                    },
                )
                AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
                return roadAddress
            }

            PlaceAutocompleteCacheEntry.NotSearchable -> {
                AnalysisUtil.debug("resolve: firestore hit=not_searchable, using jibun")
                AnalysisUtil.logEvent(
                    "firestore_hit",
                    Bundle().apply {
                        putString("package", poi.packageName)
                        putString("result", "not_searchable")
                    },
                )
                AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_JIBUN)
                return jibunAddress
            }

            null -> {
                AnalysisUtil.debug("resolve: firestore miss")
            }
        }

        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
            AnalysisUtil.debug("resolve: update disabled by RC, using road")
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            return roadAddress
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
        AnalysisUtil.debug("resolve: places_api match=$matched")

        return if (matched == null) {
            AnalysisUtil.debug("resolve: places error, using road (no cache set)")
            roadAddress
        } else if (matched) {
            AnalysisUtil.debug("resolve: cache set=searchable, using road")
            AnalysisUtil.logEvent(
                "firestore_set",
                Bundle().apply {
                    putString("package", poi.packageName)
                    putString("result", "searchable")
                },
            )
            cacheClient.cache(roadAddress, searchable = true)
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            roadAddress
        } else {
            AnalysisUtil.debug("resolve: cache set=not_searchable, using jibun")
            AnalysisUtil.logEvent(
                "firestore_set",
                Bundle().apply {
                    putString("package", poi.packageName)
                    putString("result", "not_searchable")
                },
            )
            cacheClient.cache(roadAddress, searchable = false)
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_JIBUN)
            jibunAddress
        }
    }
}
