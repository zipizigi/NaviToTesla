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
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.PoiAddressDao
import me.zipi.navitotesla.db.PoiAddressEntity
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class DestinationAddressResolverTest {
    private val poi =
        Poi(
            poiName = "테스트 POI",
            roadAddress = "서울특별시 종로구 세종대로 175",
            address = "서울특별시 종로구 세종로 1-57",
            packageName = "com.example.navi",
        )

    private lateinit var dao: PoiAddressDao
    private lateinit var repository: AppRepository
    private lateinit var fakeMatcher: PlaceAutocompleteMatcher
    private lateinit var fakeCacheClient: PlaceAutocompleteCacheClient

    @Before
    fun setUp() {
        assumeTrue("playstore flavor 에서만 의미", BuildConfig.BUILD_MODE == "playstore")
        dao = mockk(relaxed = true)
        coEvery { dao.findPoiByPackage(any(), any()) } returns null

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit

        mockkObject(AppDatabase)
        val db = mockk<AppDatabase>()
        every { db.poiAddressDao() } returns dao
        every { AppDatabase.getInstance() } returns db

        mockkObject(AppRepository)
        repository = mockk(relaxed = true)
        every { AppRepository.getInstance() } returns repository

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
    fun `로컬 캐시 hit 이면 sentAddress 반환하고 Firestore 호출 안함`() =
        runBlocking {
            coEvery { dao.findPoiByPackage(poi.poiName!!, poi.packageName) } returns
                PoiAddressEntity(
                    poi = poi.poiName!!,
                    packageName = poi.packageName,
                    roadAddress = "도로명 캐시",
                    jibunAddress = "지번 캐시",
                    latitude = null,
                    longitude = null,
                    registered = null,
                    isDuplicate = null,
                    sentMode = PoiAddressEntity.SENT_MODE_JIBUN,
                    created = java.util.Date(),
                )

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals("지번 캐시", result)
            coVerify(exactly = 0) { fakeCacheClient.lookup(any()) }
            coVerify(exactly = 0) { repository.markSent(any(), any()) }
        }

    @Test
    fun `lookup disabled 이면 도로명 반환하고 Firestore 조회 안함`() =
        runBlocking {
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_LOOKUP_ENABLED) } returns false
            every { RemoteConfigUtil.getBoolean(RemoteConfigUtil.KEY_GOOGLE_PLACE_CHECK_UPDATE_ENABLED) } returns false

            val result = DestinationAddressResolver.resolve(poi)

            assertEquals(poi.getRoadAddress(), result)
            coVerify(exactly = 0) { fakeCacheClient.lookup(any()) }
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_ROAD) }
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
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_ROAD) }
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
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_JIBUN) }
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
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_ROAD) }
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
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_JIBUN) }
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
            coVerify(exactly = 0) { repository.markSent(any(), any()) }
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
            coVerify(exactly = 1) { repository.markSent(poi, PoiAddressEntity.SENT_MODE_ROAD) }
        }
}
