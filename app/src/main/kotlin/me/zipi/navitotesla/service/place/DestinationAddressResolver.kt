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

    /**
     * Poi 의 도로명/구주소 중 차량에 실제로 보낼 주소를 결정한다.
     * 부작용: 결정 결과를 destination_send_cache 에 upsert.
     */
    suspend fun resolve(poi: Poi): String {
        val poiName = poi.poiName ?: return poi.getRoadAddress()
        val roadAddress = poi.getRoadAddress()
        val jibunAddress = poi.getAddress()

        // 1. 로컬 캐시 hit → 그대로 사용
        val dao = AppDatabase.getInstance().destinationSendCacheDao()
        val cached = dao.find(poiName, poi.packageName)
        if (cached != null && !cached.isExpire) {
            return cached.sentAddress
        }

        // 2. nostore 또는 jibun 이 없거나 도로명과 동일 → 도로명 그대로
        if (BuildConfig.BUILD_MODE != "playstore" || jibunAddress.isEmpty() || jibunAddress == roadAddress) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        // 3. Remote Config 분기
        val lookupEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
        if (!lookupEnabled) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        // 4. Firestore cache 조회 (positive/negative 모두 분기)
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
                // miss → 다음 단계로
            }
        }

        // 5. update_enabled 면 Places SDK 호출 + matcher 판단 + Firestore 에 결과 기록
        val updateEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
        if (!updateEnabled) {
            saveLocal(poiName, poi.packageName, roadAddress, sentAsJibun = false)
            return roadAddress
        }

        val matched =
            try {
                matcher.isMatch(roadAddress)
            } catch (e: CancellationException) {
                // 코루틴 취소는 그대로 상위로 전파해 cancellation 신호 손실 방지.
                throw e
            } catch (e: Exception) {
                AnalysisUtil.log("matcher failed: ${e.javaClass.simpleName}")
                AnalysisUtil.recordException(e)
                null
            }

        return if (matched == null) {
            // SDK/매처 실패 → 어느 결과인지 모르므로 Firestore write 하지 않음, 도로명 fallback
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
