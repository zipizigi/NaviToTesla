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

        if (poi.isCoordsAddress()) {
            AnalysisUtil.debug("classify: coords detected ($roadAddress), not_searchable")
            return Searchability.NotSearchable
        }
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
            return markUnknown(poi)
        }

        val ratio = RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO).coerceIn(0L, 100L).toInt()
        if (Random.nextInt(100) >= ratio) {
            AnalysisUtil.debug("classify: places update sampled out (ratio=$ratio), unknown")
            return markUnknown(poi)
        }

        val prefixEnabled = RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_PREFIX_ENABLED)
        val prefixSpec =
            if (prefixEnabled) {
                AddressPrefixBuilder.build(roadAddress)
            } else {
                AddressPrefixBuilder.Prefix(roadAddress, isTruncated = false)
            }

        val first = queryAndCacheSiblings(prefixSpec.prefix, roadAddress, prefixEnabled, eventParam) ?: return markUnknown(poi)

        // matchedPlaceId 추출 때문에 boolean 대신 질의 결과를 들고 간다. null 이면 NotSearchable.
        // prefix 에서 못 찾고 prefix≠full(절단) 이면, prefix 자동완성 누락 가능성이 있어 full 로 한 번 더 확인한다.
        val resolved: AutocompleteResult? =
            when {
                first.matched -> {
                    first
                }

                prefixSpec.isTruncated -> {
                    (queryAndCacheSiblings(roadAddress, roadAddress, prefixEnabled, eventParam) ?: return markUnknown(poi))
                        .takeIf { it.matched }
                }

                else -> {
                    null
                }
            }

        val searchable = resolved != null
        AnalysisUtil.logEvent(
            "firestore_set",
            Bundle().apply {
                putString("package", poi.packageName)
                putString("result", if (searchable) "searchable" else "not_searchable")
            },
        )
        cacheClient.cache(roadAddress, searchable = searchable, placesId = resolved?.matchedPlaceId)
        val result = if (searchable) Searchability.Searchable else Searchability.NotSearchable
        AppRepository.getInstance().markClassified(poi, result)
        return result
    }

    private suspend fun markUnknown(poi: Poi): Searchability {
        AppRepository.getInstance().markClassified(poi, Searchability.Unknown)
        return Searchability.Unknown
    }

    // queryInput 으로 자동완성 조회(매칭은 target 기준) + (prefix 활성 시) 형제 캐싱. API 오류 시 null → Unknown.
    private suspend fun queryAndCacheSiblings(
        queryInput: String,
        target: String,
        prefixEnabled: Boolean,
        eventParam: Bundle,
    ): AutocompleteResult? {
        val result =
            try {
                AnalysisUtil.logEvent("places_api_call", eventParam)
                matcher.query(queryInput, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AnalysisUtil.recordException(e)
                return null
            }
        if (prefixEnabled) cacheClient.cacheSiblings(result.predictions)
        return result
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
