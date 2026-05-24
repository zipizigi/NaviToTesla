package me.zipi.navitotesla.service.place

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.DestinationSendCacheDao
import me.zipi.navitotesla.db.DestinationSendCacheEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class DestinationAddressResolverTest {
    private val poi =
        Poi(
            poiName = "테스트 POI",
            roadAddress = "서울특별시 종로구 세종대로 175",
            address = "서울특별시 종로구 세종로 1-57",
            packageName = "com.example.navi",
        )

    private lateinit var dao: DestinationSendCacheDao
    private lateinit var fakeMatcher: PlaceAutocompleteMatcher
    private lateinit var fakeCacheClient: PlaceAutocompleteCacheClient

    @Before
    fun setUp() {
        assumeTrue("playstore flavor 에서만 의미", BuildConfig.BUILD_MODE == "playstore")
        dao = mockk(relaxed = true)
        coEvery { dao.find(any(), any()) } returns null

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit

        mockkObject(AppDatabase)
        val db = mockk<AppDatabase>()
        every { db.destinationSendCacheDao() } returns dao
        every { AppDatabase.getInstance() } returns db

        mockkObject(RemoteConfigUtil)

        fakeMatcher = mockk()
        fakeCacheClient = mockk(relaxed = true)
        DestinationAddressResolver.matcher = fakeMatcher
        DestinationAddressResolver.cacheClient = fakeCacheClient
    }

    @After
    fun tearDown() {
        unmockkAll()
        DestinationAddressResolver.matcher = RealPlaceAutocompleteMatcher()
        DestinationAddressResolver.cacheClient = FirestorePlaceAutocompleteCacheClient
    }

    @Test
    fun `lookup disabled 이면 도로명 반환하고 Firestore 조회 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns false
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns false

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 0) { fakeCacheClient.lookup(any()) }
        }

    @Test
    fun `Firestore Searchable hit 이면 도로명 반환하고 SDK 호출 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
            coEvery { fakeCacheClient.lookup(any()) } returns PlaceAutocompleteCacheEntry.Searchable

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 0) { fakeMatcher.isMatch(any()) }
            coVerify(exactly = 0) { fakeCacheClient.cache(any(), any()) }
        }

    @Test
    fun `Firestore NotSearchable hit 이면 구주소 반환하고 SDK 호출 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns false
            coEvery { fakeCacheClient.lookup(any()) } returns PlaceAutocompleteCacheEntry.NotSearchable

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getAddress(), result)
            coVerify(exactly = 0) { fakeMatcher.isMatch(any()) }
        }

    @Test
    fun `로컬 캐시 hit 이면 그대로 반환하고 Firestore 호출 안함`() =
        runBlocking {
            coEvery { dao.find(poi.poiName!!, poi.packageName) } returns
                DestinationSendCacheEntity(
                    poi = poi.poiName!!,
                    sentAddress = "캐시된 주소",
                    sentAsJibun = true,
                    packageName = poi.packageName,
                    created = Date(),
                )

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals("캐시된 주소", result)
            coVerify(exactly = 0) { fakeCacheClient.lookup(any()) }
        }

    @Test
    fun `miss + update enabled + matcher true 면 도로명 반환 + Firestore Searchable write`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
            coEvery { fakeCacheClient.lookup(any()) } returns null
            coEvery { fakeMatcher.isMatch(any()) } returns true

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 1) { fakeCacheClient.cache(poi.getRoadAddress(), true) }
        }

    @Test
    fun `miss + update enabled + matcher false 면 구주소 반환 + Firestore NotSearchable write`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
            coEvery { fakeCacheClient.lookup(any()) } returns null
            coEvery { fakeMatcher.isMatch(any()) } returns false

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getAddress(), result)
            coVerify(exactly = 1) { fakeCacheClient.cache(poi.getRoadAddress(), false) }
        }

    @Test
    fun `miss + update enabled + matcher throw 면 도로명 fallback + Firestore write 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns true
            coEvery { fakeCacheClient.lookup(any()) } returns null
            coEvery { fakeMatcher.isMatch(any()) } throws RuntimeException("simulated SDK failure")

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 0) { fakeCacheClient.cache(any(), any()) }
        }

    @Test
    fun `miss + update disabled 면 도로명 반환 + SDK 호출 안함 + Firestore write 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns true
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns false
            coEvery { fakeCacheClient.lookup(any()) } returns null

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 0) { fakeMatcher.isMatch(any()) }
            coVerify(exactly = 0) { fakeCacheClient.cache(any(), any()) }
        }
}
