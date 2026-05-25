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

        val dao = AppDatabase.getInstance().poiAddressDao()
        val cached = dao.findPoiByPackage(poiName, poi.packageName)
        if (cached != null && !cached.isExpire) {
            cached.sentAddress?.let { return it }
        }

        val firebaseDisabledFlavor = !BuildConfig.DEBUG && BuildConfig.BUILD_MODE != "playstore"
        if (firebaseDisabledFlavor || jibunAddress.isEmpty() || jibunAddress == roadAddress) {
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            return roadAddress
        }

        val lookupEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
        if (!lookupEnabled) {
            AppRepository.getInstance().markSent(poi, PoiAddressEntity.SENT_MODE_ROAD)
            return roadAddress
        }

        AnalysisUtil.logEvent("firestore_get", eventParam)
        when (cacheClient.lookup(roadAddress)) {
            PlaceAutocompleteCacheEntry.Searchable -> {
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
                // miss → 다음 단계
            }
        }

        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
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

        return if (matched == null) {
            roadAddress
        } else if (matched) {
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
