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

    private fun entity(
        searchable: Boolean?,
        registered: Boolean,
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
        sentMode = null,
        searchable = searchable,
        created = java.util.Date(),
    )

    private class FakeCacheClient : PlaceAutocompleteCacheClient {
        var lookupResult: PlaceAutocompleteCacheEntry? = null
        val cached = mutableListOf<Pair<String, Boolean>>()

        override suspend fun lookup(address: String) = lookupResult

        override suspend fun cache(
            address: String,
            searchable: Boolean,
        ) {
            cached += address to searchable
        }
    }

    private class FakeMatcher : PlaceAutocompleteMatcher {
        var matchResult: Boolean = false

        override suspend fun isMatch(input: String) = matchResult
    }
}
