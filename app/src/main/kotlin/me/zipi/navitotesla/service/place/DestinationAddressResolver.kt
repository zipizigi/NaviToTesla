package me.zipi.navitotesla.service.place

import kotlinx.coroutines.CancellationException
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.DestinationSendCacheEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import java.util.Date

object DestinationAddressResolver {
    @Volatile
    var matcher: PlaceAutocompleteMatcher = RealPlaceAutocompleteMatcher()

    @Volatile
    var cacheClient: PlaceAutocompleteCacheClient = FirestorePlaceAutocompleteCacheClient

    suspend fun resolve(poi: Poi): String {
        val poiName = poi.poiName ?: return poi.getRoadAddress()
        val roadAddress = poi.getRoadAddress()
        val jibunAddress = poi.getAddress()

        val dao = AppDatabase.getInstance().destinationSendCacheDao()
        val cached = dao.find(poiName, poi.packageName)
        if (cached != null && !cached.isExpire) {
            return cached.sentAddress
        }

        // nostore release 는 Firebase 비활성. debug 빌드는 flavor 무관 통과해 검증 가능.
        val firebaseDisabledFlavor = !BuildConfig.DEBUG && BuildConfig.BUILD_MODE != "playstore"
        if (firebaseDisabledFlavor || jibunAddress.isEmpty() || jibunAddress == roadAddress) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        val lookupEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
        if (!lookupEnabled) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        when (cacheClient.lookup(roadAddress)) {
            PlaceAutocompleteCacheEntry.Searchable -> {
                saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
                return roadAddress
            }

            PlaceAutocompleteCacheEntry.NotSearchable -> {
                saveLocal(poiName, poi.packageName, jibunAddress, sentAsJibun = true)
                return jibunAddress
            }

            null -> {
                // miss → 다음 단계
            }
        }

        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        val matched =
            try {
                matcher.isMatch(roadAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
                null
            }

        return if (matched == null) {
            // 일시 오류로 잘못된 negative 가 영구 기록되지 않도록 cache write skip.
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            roadAddress
        } else if (matched) {
            cacheClient.cache(roadAddress, searchable = true)
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            roadAddress
        } else {
            cacheClient.cache(roadAddress, searchable = false)
            saveLocal(poiName, poi.packageName, jibunAddress, sentAsJibun = true)
            jibunAddress
        }
    }

    private suspend fun saveLocal(
        poi: String,
        packageName: String,
        sentAddress: String,
        sentAsJibun: Boolean,
    ) {
        val dao = AppDatabase.getInstance().destinationSendCacheDao()
        dao.upsert(
            DestinationSendCacheEntity(
                poi = poi,
                sentAddress = sentAddress,
                sentAsJibun = sentAsJibun,
                packageName = packageName,
                created = Date(),
            ),
        )
    }
}
