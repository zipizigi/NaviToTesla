package me.zipi.navitotesla.service.place

import com.google.android.gms.tasks.Tasks
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class FirestorePlaceAutocompleteCacheClientTest {
    private lateinit var docRef: DocumentReference

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit
        every { AnalysisUtil.debug(any()) } returns Unit
        every { AnalysisUtil.warn(any()) } returns Unit
        every { AnalysisUtil.logEvent(any(), any()) } returns Unit
        every { AnalysisUtil.recordException(any()) } returns Unit
        mockkObject(RemoteConfigUtil)
        every { RemoteConfigUtil.getLong(any()) } returns 0L

        mockkStatic(FirebaseFirestore::class)
        val firestore = mockk<FirebaseFirestore>()
        val collection = mockk<CollectionReference>()
        docRef = mockk()
        every { FirebaseFirestore.getInstance() } returns firestore
        every { firestore.collection(any()) } returns collection
        every { collection.document(any<String>()) } returns docRef
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `lookup permission denied returns PermissionDenied and logs event instead of recording exception`() =
        runBlocking {
            every { docRef.get() } returns
                Tasks.forException(
                    FirebaseFirestoreException(
                        "PERMISSION_DENIED: Missing or insufficient permissions.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ),
                )

            assertSame(
                PlaceAutocompleteCacheEntry.PermissionDenied,
                FirestorePlaceAutocompleteCacheClient.lookup("서울특별시 중구 세종대로 110"),
            )
            verify { AnalysisUtil.logEvent("firestore_permission_denied", any()) }
            verify(exactly = 0) { AnalysisUtil.recordException(any()) }
        }

    @Test
    fun `lookup unavailable still records exception`() =
        runBlocking {
            every { docRef.get() } returns
                Tasks.forException(
                    FirebaseFirestoreException(
                        "UNAVAILABLE: Failed to get document",
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                    ),
                )

            assertNull(FirestorePlaceAutocompleteCacheClient.lookup("서울특별시 중구 세종대로 110"))
            verify(exactly = 0) { AnalysisUtil.logEvent("firestore_permission_denied", any()) }
            verify { AnalysisUtil.recordException(any()) }
        }

    @Test
    fun `cache permission denied logs event instead of recording exception`() =
        runBlocking {
            every { docRef.set(any()) } returns
                Tasks.forException(
                    FirebaseFirestoreException(
                        "PERMISSION_DENIED: Missing or insufficient permissions.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ),
                )

            FirestorePlaceAutocompleteCacheClient.cache("서울특별시 중구 세종대로 110", searchable = true)
            verify { AnalysisUtil.logEvent("firestore_permission_denied", any()) }
            verify(exactly = 0) { AnalysisUtil.recordException(any()) }
        }

    @Test
    fun `lookup miss returns null without events`() =
        runBlocking {
            val snapshot = mockk<DocumentSnapshot>()
            every { snapshot.exists() } returns false
            every { docRef.get() } returns Tasks.forResult(snapshot)

            assertNull(FirestorePlaceAutocompleteCacheClient.lookup("서울특별시 중구 세종대로 110"))
            verify(exactly = 0) { AnalysisUtil.logEvent("firestore_permission_denied", any()) }
            verify(exactly = 0) { AnalysisUtil.recordException(any()) }
        }
}
