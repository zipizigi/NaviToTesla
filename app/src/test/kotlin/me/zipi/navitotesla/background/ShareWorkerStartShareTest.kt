package me.zipi.navitotesla.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import me.zipi.navitotesla.TestApplication
import me.zipi.navitotesla.util.AnalysisUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = TestApplication::class)
class ShareWorkerStartShareTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // JVM 환경에서 Firebase / AnalysisUtil 부수효과 차단.
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit

        // WorkManager.Companion 의 getInstance 를 가로채 enqueue 만 캡처.
        workManager = mockk(relaxed = true)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startShare enqueues a ShareWorker WorkRequest with TMap input data`() {
        ShareWorker.startShare(
            context,
            packageName = "com.skt.tmap.ku",
            notificationTitle = "경로주행",
            notificationText = "내 위치 > 강남역",
        )

        val captured = slot<OneTimeWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "share_com.skt.tmap.ku",
                ExistingWorkPolicy.KEEP,
                capture(captured),
            )
        }

        val spec = captured.captured.workSpec
        assertEquals(ShareWorker::class.java.name, spec.workerClassName)
        assertEquals("com.skt.tmap.ku", spec.input.getString("packageName"))
        assertEquals("경로주행", spec.input.getString("notificationTitle"))
        assertEquals("내 위치 > 강남역", spec.input.getString("notificationText"))
    }

    @Test
    fun `startShare enqueues a ShareWorker WorkRequest with Kakao input data`() {
        ShareWorker.startShare(
            context,
            packageName = "com.locnall.KimGiSa",
            notificationTitle = "길안내 주행 중",
            notificationText = "목적지 : 송파구청",
        )

        val captured = slot<OneTimeWorkRequest>()
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                "share_com.locnall.KimGiSa",
                ExistingWorkPolicy.KEEP,
                capture(captured),
            )
        }

        val spec = captured.captured.workSpec
        assertEquals(ShareWorker::class.java.name, spec.workerClassName)
        assertEquals("com.locnall.KimGiSa", spec.input.getString("packageName"))
        assertEquals("길안내 주행 중", spec.input.getString("notificationTitle"))
        assertEquals("목적지 : 송파구청", spec.input.getString("notificationText"))
    }
}
