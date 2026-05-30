package me.zipi.navitotesla.service.place

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressDao
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DestinationAddressResolverClassifyTest {
    private lateinit var dao: PoiAddressDao
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository
    private lateinit var fakeCache: FakeCacheClient
    private lateinit var fakeMatcher: FakeMatcher
    private val originalCache = DestinationAddressResolver.cacheClient
    private val originalMatcher = DestinationAddressResolver.matcher

    private val poi =
        Poi(
            poiName = "서울특별시청",
            roadAddress = "서울특별시 중구 세종대로 110",
            address = "서울특별시 중구 태평로1가 31",
            latitude = null,
            longitude = null,
            packageName = "com.example",
        )

    @Before
    fun setUp() {
        // Firebase 의존성 우회 (AnalysisUtil 의 static init 이 Crashlytics 를 호출).
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit
        every { AnalysisUtil.debug(any()) } returns Unit
        every { AnalysisUtil.warn(any()) } returns Unit
        every { AnalysisUtil.logEvent(any(), any()) } returns Unit
        every { AnalysisUtil.recordException(any()) } returns Unit

        // RC 도 mock — Resolver 가 KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED 등을 직접 조회.
        mockkObject(RemoteConfigUtil)
        every { RemoteConfigUtil.getBoolean(any()) } returns false
        every { RemoteConfigUtil.getLong(any()) } returns 0L
        every { RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_COOLDOWN_HOURS) } returns 24L

        dao = mockk(relaxed = true)
        db = mockk(relaxed = true)
        repo = mockk(relaxed = true)
        coEvery { dao.findPoiByPackage(any(), any()) } returns null
        // AppDatabase / AppRepository singleton 주입: 본 테스트는 Robolectric 없이 동작해야 하므로
        // 정적 의존성 우회 — Resolver 가 호출하는 AppDatabase.getInstance(), AppRepository.getInstance() 을
        // mockk-static 으로 대체.
        mockkObject(AppDatabase.Companion)
        mockkObject(AppRepository.Companion)
        every { AppDatabase.getInstance() } returns db
        every { AppRepository.getInstance() } returns repo
        every { db.poiAddressDao() } returns dao

        fakeCache = FakeCacheClient()
        fakeMatcher = FakeMatcher()
        DestinationAddressResolver.cacheClient = fakeCache
        DestinationAddressResolver.matcher = fakeMatcher
    }

    @After
    fun tearDown() {
        DestinationAddressResolver.cacheClient = originalCache
        DestinationAddressResolver.matcher = originalMatcher
        unmockkAll()
    }

    @Test
    fun `local cache hit returns cached searchable`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(any(), any()) } returns entity(searchable = true, registered = false)
            assertSame(Searchability.Searchable, DestinationAddressResolver.classify(poi))
        }

    @Test
    fun `local cache hit returns cached not_searchable`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(any(), any()) } returns entity(searchable = false, registered = false)
            assertSame(Searchability.NotSearchable, DestinationAddressResolver.classify(poi))
        }

    @Test
    fun `null cached searchable with RC lookup_enabled=false returns Unknown`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(any(), any()) } returns entity(searchable = null, registered = false)
            // 기본 setUp 에서 모든 RC key 는 false → lookup_enabled=false 경로.
            every { RemoteConfigUtil.getBoolean(any()) } returns false
            // firestore 까지 가지 않아야 함 — lookupResult 를 설정해도 무시되어야 정상.
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Unknown, DestinationAddressResolver.classify(poi))
        }

    @Test
    fun `null cached searchable with RC lookup_enabled=true and firestore hit returns Searchable`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(any(), any()) } returns entity(searchable = null, registered = false)
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            // 다른 RC key 들은 기본 false 유지.
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Searchable, DestinationAddressResolver.classify(poi))
        }

    @Test
    fun `cooldown active returns Unknown without firestore lookup`() =
        runBlocking {
            // lookup_enabled=true 인데도 lastCheckedAt 이 1h 전 → 쿨다운(24h) 안이라 firestore/places 둘 다 skip.
            coEvery { dao.findPoiByPackage(any(), any()) } returns
                entity(
                    searchable = null,
                    registered = false,
                    lastCheckedAt = System.currentTimeMillis() - 60L * 60L * 1000L,
                )
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Unknown, DestinationAddressResolver.classify(poi))
            // firestore lookup 까지 도달하지 않았음을 확인.
            assertEquals(0, fakeCache.lookupCount)
        }

    @Test
    fun `cooldown expired falls through to firestore lookup`() =
        runBlocking {
            // lastCheckedAt 이 25h 전 → 24h 쿨다운 만료, firestore lookup 진행.
            coEvery { dao.findPoiByPackage(any(), any()) } returns
                entity(
                    searchable = null,
                    registered = false,
                    lastCheckedAt = System.currentTimeMillis() - 25L * 60L * 60L * 1000L,
                )
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Searchable, DestinationAddressResolver.classify(poi))
            assertEquals(1, fakeCache.lookupCount)
        }

    @Test
    fun `clock skew - negative elapsed treated as cooldown expired`() =
        runBlocking {
            // lastCheckedAt 이 미래(now + 10분) — 디바이스 시계 역행 시나리오.
            coEvery { dao.findPoiByPackage(any(), any()) } returns
                entity(
                    searchable = null,
                    registered = false,
                    lastCheckedAt = System.currentTimeMillis() + 10L * 60L * 1000L,
                )
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            // 음수 elapsed → 쿨다운 무시하고 firestore 진행.
            assertSame(Searchability.Searchable, DestinationAddressResolver.classify(poi))
            assertEquals(1, fakeCache.lookupCount)
        }

    @Test
    fun `cooldown overflow guard - huge cooldownHours coerced safely`() =
        runBlocking {
            // RC 값이 Long.MAX_VALUE 라 곱셈 overflow 위험 — coerceIn 으로 안전 범위 자르고 쿨다운 적용되어야.
            every { RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_COOLDOWN_HOURS) } returns Long.MAX_VALUE
            coEvery { dao.findPoiByPackage(any(), any()) } returns
                entity(
                    searchable = null,
                    registered = false,
                    lastCheckedAt = System.currentTimeMillis() - 60L * 60L * 1000L,
                )
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Unknown, DestinationAddressResolver.classify(poi))
            assertEquals(0, fakeCache.lookupCount)
        }

    @Test
    fun `cooldown disabled by RC=0 still falls through`() =
        runBlocking {
            // cooldownHours=0 → 쿨다운 기능 off. lastCheckedAt 이 최근이어도 firestore 진행.
            every { RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_COOLDOWN_HOURS) } returns 0L
            coEvery { dao.findPoiByPackage(any(), any()) } returns
                entity(
                    searchable = null,
                    registered = false,
                    lastCheckedAt = System.currentTimeMillis() - 60L * 1000L,
                )
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            fakeCache.lookupResult = PlaceAutocompleteCacheEntry.Searchable
            assertSame(Searchability.Searchable, DestinationAddressResolver.classify(poi))
            assertEquals(1, fakeCache.lookupCount)
        }

    @Test
    fun `sampled out path records cooldown via markClassified(null)`() =
        runBlocking {
            // lookup/update 둘 다 enabled, firestore miss, ratio=0 → 항상 sampled out.
            coEvery { dao.findPoiByPackage(any(), any()) } returns null
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
            } returns true
            every {
                RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO)
            } returns 0L
            fakeCache.lookupResult = null

            assertSame(Searchability.Unknown, DestinationAddressResolver.classify(poi))
            io.mockk.coVerify { repo.markClassified(poi, Searchability.Unknown) }
        }

    @Test
    fun `places match true caches searchable`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_PREFIX_ENABLED) } returns false
            every { RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO) } returns 100L
            fakeMatcher.results["서울특별시 중구 세종대로 110"] =
                AutocompleteResult(
                    listOf(PlacePrediction("대한민국 서울특별시 중구 세종대로 110", "pid")),
                    matched = true,
                    matchedPlaceId = "pid",
                )

            val result = DestinationAddressResolver.classify(poi)

            assertEquals(Searchability.Searchable, result)
            assertEquals("서울특별시 중구 세종대로 110" to true, fakeCache.cached.single())
            assertEquals("pid", fakeCache.cachedPlacesId.single())
            // prefix-disabled 경로는 형제 캐싱을 하지 않는다.
            assertTrue(fakeCache.siblingBatches.isEmpty())
        }

    @Test
    fun `places api exception path records cooldown via markClassified(null)`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(any(), any()) } returns null
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED)
            } returns true
            every {
                RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED)
            } returns true
            every {
                RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO)
            } returns 100L
            fakeCache.lookupResult = null
            fakeMatcher.throwOnQuery = RuntimeException("rate limit")

            assertSame(Searchability.Unknown, DestinationAddressResolver.classify(poi))
            io.mockk.coVerify { repo.markClassified(poi, Searchability.Unknown) }
        }

    private fun enablePrefixFlow() {
        every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
        every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
        every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_PREFIX_ENABLED) } returns true
        every { RemoteConfigUtil.getLong(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_RATIO) } returns 100L
    }

    private val numberPoi =
        Poi(
            poiName = "목적지",
            roadAddress = "서울특별시 강남구 영동대로 1234",
            address = "",
            latitude = null,
            longitude = null,
            packageName = "com.example",
        )

    @Test
    fun `prefix hit caches target and siblings as searchable`() =
        runBlocking {
            enablePrefixFlow()
            // 1차 prefix "서울특별시 강남구 영동대로 123" 응답에 타깃 포함 + 형제
            fakeMatcher.results["서울특별시 강남구 영동대로 123"] =
                AutocompleteResult(
                    listOf(
                        PlacePrediction("대한민국 서울특별시 강남구 영동대로 1234", "p1"),
                        PlacePrediction("대한민국 서울특별시 강남구 영동대로 1230", "p2"),
                    ),
                    matched = true,
                    matchedPlaceId = "p1",
                )

            val result = DestinationAddressResolver.classify(numberPoi)

            assertEquals(Searchability.Searchable, result)
            assertEquals(listOf("서울특별시 강남구 영동대로 123"), fakeMatcher.queried) // 1호출만
            assertEquals(2, fakeCache.siblingBatches.single().size)
            assertEquals("서울특별시 강남구 영동대로 1234" to true, fakeCache.cached.single())
            assertEquals("p1", fakeCache.cachedPlacesId.single())
        }

    @Test
    fun `prefix miss truncated then full miss is not searchable (two calls)`() =
        runBlocking {
            enablePrefixFlow()
            // prefix 미매칭(건수 무관) → 절단됐으므로 항상 2차 full. full 도 미매칭(미설정=빈결과) → NotSearchable.
            fakeMatcher.results["서울특별시 강남구 영동대로 123"] =
                AutocompleteResult(
                    List(4) { PlacePrediction("대한민국 서울특별시 강남구 영동대로 12${it}0", "p$it") },
                    matched = false,
                    matchedPlaceId = null,
                )

            val result = DestinationAddressResolver.classify(numberPoi)

            assertEquals(Searchability.NotSearchable, result)
            assertEquals(
                listOf("서울특별시 강남구 영동대로 123", "서울특별시 강남구 영동대로 1234"),
                fakeMatcher.queried,
            ) // 1차 미스 → 2차 full = 2호출
            assertEquals("서울특별시 강남구 영동대로 1234" to false, fakeCache.cached.single())
            assertEquals(null, fakeCache.cachedPlacesId.single())
            // 1차(4건)+2차(0건) 모두 형제 캐싱 시도.
            assertEquals(2, fakeCache.siblingBatches.size)
            assertEquals(4, fakeCache.siblingBatches[0].size)
        }

    @Test
    fun `prefix miss truncated then full hit is searchable (two calls)`() =
        runBlocking {
            enablePrefixFlow()
            // 1차 prefix 미매칭(건수와 무관) → 절단됐으므로 2차 full 로 확인
            fakeMatcher.results["서울특별시 강남구 영동대로 123"] =
                AutocompleteResult(
                    List(5) { PlacePrediction("대한민국 서울특별시 강남구 영동대로 12${it}9", "p$it") },
                    matched = false,
                    matchedPlaceId = null,
                )
            // 2차 full: 타깃 매칭
            fakeMatcher.results["서울특별시 강남구 영동대로 1234"] =
                AutocompleteResult(
                    listOf(PlacePrediction("대한민국 서울특별시 강남구 영동대로 1234", "pT")),
                    matched = true,
                    matchedPlaceId = "pT",
                )

            val result = DestinationAddressResolver.classify(numberPoi)

            assertEquals(Searchability.Searchable, result)
            assertEquals(
                listOf("서울특별시 강남구 영동대로 123", "서울특별시 강남구 영동대로 1234"),
                fakeMatcher.queried,
            ) // 2호출
            assertEquals(2, fakeCache.siblingBatches.size) // 1차+2차 형제 캐싱
            // 2차 질의가 searchable 판정을 냈으므로 그 matchedPlaceId 가 저장되어야 한다.
            assertEquals("pT", fakeCache.cachedPlacesId.single())
        }

    @Test
    fun `prefix miss with exactly max but second query fails returns Unknown`() =
        runBlocking {
            enablePrefixFlow()
            // 1차: 정확히 5건 + 타깃 미포함 → 모호 → 2차 full 시도
            fakeMatcher.results["서울특별시 강남구 영동대로 123"] =
                AutocompleteResult(
                    List(5) { PlacePrediction("대한민국 서울특별시 강남구 영동대로 12${it}9", "p$it") },
                    matched = false,
                    matchedPlaceId = null,
                )
            // 2차 full 질의는 예외 → Unknown
            fakeMatcher.throwForInput["서울특별시 강남구 영동대로 1234"] = RuntimeException("boom")

            val result = DestinationAddressResolver.classify(numberPoi)

            assertEquals(Searchability.Unknown, result)
            assertEquals(
                listOf("서울특별시 강남구 영동대로 123", "서울특별시 강남구 영동대로 1234"),
                fakeMatcher.queried,
            ) // 정확히 2호출
        }

    private val singleDigitPoi =
        Poi(
            poiName = "목적지",
            roadAddress = "서울특별시 강남구 영동대로 5",
            address = "",
            latitude = null,
            longitude = null,
            packageName = "com.example",
        )

    @Test
    fun `not truncated with exactly max and miss is not searchable in one call`() =
        runBlocking {
            enablePrefixFlow()
            // 단자리 번지 → prefix 절단 없음(isTruncated=false). 5건 반환 + 미매칭 → 2차 없이 NotSearchable.
            fakeMatcher.results["서울특별시 강남구 영동대로 5"] =
                AutocompleteResult(
                    List(5) { PlacePrediction("대한민국 서울특별시 강남구 영동대로 ${it}9", "p$it") },
                    matched = false,
                    matchedPlaceId = null,
                )

            val result = DestinationAddressResolver.classify(singleDigitPoi)

            assertEquals(Searchability.NotSearchable, result)
            assertEquals(listOf("서울특별시 강남구 영동대로 5"), fakeMatcher.queried) // 1호출
            assertEquals("서울특별시 강남구 영동대로 5" to false, fakeCache.cached.single())
        }

    private fun entity(
        searchable: Boolean?,
        registered: Boolean,
        lastCheckedAt: Long? = null,
        created: java.util.Date = java.util.Date(),
    ) = PoiAddressEntity(
        id = 1,
        poi = "서울특별시청",
        packageName = "com.example",
        roadAddress = "서울특별시 중구 세종대로 110",
        jibunAddress = "서울특별시 중구 태평로1가 31",
        latitude = null,
        longitude = null,
        registered = registered,
        isDuplicate = false,
        searchable = searchable,
        created = created,
        lastCheckedAt = lastCheckedAt,
    )

    private class FakeCacheClient : PlaceAutocompleteCacheClient {
        var lookupResult: PlaceAutocompleteCacheEntry? = null
        var lookupCount: Int = 0
        val cached = mutableListOf<Pair<String, Boolean>>()
        val cachedPlacesId = mutableListOf<String?>()
        val siblingBatches = mutableListOf<List<PlacePrediction>>()

        override suspend fun lookup(address: String): PlaceAutocompleteCacheEntry? {
            lookupCount++
            return lookupResult
        }

        override suspend fun cache(
            address: String,
            searchable: Boolean,
            placesId: String?,
        ) {
            cached += address to searchable
            cachedPlacesId += placesId
        }

        override suspend fun cacheSiblings(siblings: List<PlacePrediction>) {
            siblingBatches += siblings
        }
    }

    private class FakeMatcher : PlaceAutocompleteMatcher {
        // 입력별 반환 결과. 미설정 입력은 빈 결과(매칭 실패).
        val results = mutableMapOf<String, AutocompleteResult>()
        val queried = mutableListOf<String>()
        var throwOnQuery: Throwable? = null
        val throwForInput = mutableMapOf<String, Throwable>()

        override suspend fun query(
            queryInput: String,
            target: String,
        ): AutocompleteResult {
            queried += queryInput
            throwForInput[queryInput]?.let { throw it }
            throwOnQuery?.let { throw it }
            return results[queryInput] ?: AutocompleteResult(emptyList(), false, null)
        }
    }
}
