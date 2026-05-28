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
        val rawCached = dao.findPoiByPackage(poiName, poi.packageName)
        // 쿨다운(default 24h)은 lastCheckedAt 기반. created 만료 여부와 무관 — expired row 라도
        // 직전 check 가 24h 이내면 Firestore/Places API 둘 다 skip.
        if (rawCached != null &&
            rawCached.searchability == Searchability.Unknown &&
            isWithinCooldown(rawCached.lastCheckedAt)
        ) {
            AnalysisUtil.debug("classify: cooldown active (lastCheckedAt=${rawCached.lastCheckedAt}), unknown")
            AnalysisUtil.logEvent("place_check_cooldown_skip", eventParam)
            return Searchability.Unknown
        }
        val cached = rawCached?.takeUnless { it.isExpire }
        when (cached?.searchability) {
            Searchability.Searchable -> {
                AnalysisUtil.debug("classify: local cache hit, searchable")
                return Searchability.Searchable
            }
            Searchability.NotSearchable -> {
                AnalysisUtil.debug("classify: local cache hit, not_searchable")
                return Searchability.NotSearchable
            }
            Searchability.Unknown, null -> Unit
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
                // Places API 예외(레이트 리밋 등). 다음 호출 때 쿨다운으로 재시도를 막는다.
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
                .coerceIn(0L, MAX_COOLDOWN_HOURS) // overflow 방지
        if (cooldownHours <= 0L) return false
        val cooldownMs = cooldownHours * 60L * 60L * 1000L
        val elapsedMs = System.currentTimeMillis() - lastCheckedAt
        // 시계 역행(elapsedMs < 0) 시 쿨다운 만료로 처리. 시계 따라잡힐 때까지 무한 잠금 방지.
        if (elapsedMs < 0L) return false
        return elapsedMs < cooldownMs
    }

    // cooldownHours * 3_600_000L 이 Long overflow 되지 않는 최대값. (Long.MAX_VALUE / 3_600_000 ≈ 2.56e12)
    private const val MAX_COOLDOWN_HOURS = Long.MAX_VALUE / (60L * 60L * 1000L)
}
