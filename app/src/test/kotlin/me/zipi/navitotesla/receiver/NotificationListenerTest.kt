package me.zipi.navitotesla.receiver

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import me.zipi.navitotesla.TestApplication
import me.zipi.navitotesla.background.ShareWorker
import me.zipi.navitotesla.background.VersionCheckWorker
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.RemoteConfigUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 핵심 회귀 방지: 지원하는 내비 앱(TMap / Kakao)의 알림이 들어오면 ShareWorker 가 큐잉되어야 하고,
 * 지원하지 않는 패키지는 무시되어야 한다. PoiFinder 호출과 WorkManager 동작 자체는 의존성이라
 * 여기선 직접 검증하지 않고, ShareWorker.startShare 가 정확한 인자로 불리는지 spy 로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = TestApplication::class)
class NotificationListenerTest {
    private lateinit var context: Context
    private lateinit var listener: NotificationListener

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit
        every { AnalysisUtil.logEvent(any(), any()) } returns Unit
        every { AnalysisUtil.setCustomKey(any(), any<String>()) } returns Unit

        mockkObject(ShareWorker.Companion)
        every { ShareWorker.startShare(any(), any(), any(), any()) } returns Unit

        mockkObject(VersionCheckWorker.Companion)
        every { VersionCheckWorker.startVersionCheck(any()) } returns Unit

        mockkObject(NaviToTeslaAccessibilityService.Companion)
        every { NaviToTeslaAccessibilityService.notifyIfAvailable(any(), any()) } returns Unit

        mockkObject(RemoteConfigUtil)
        every { RemoteConfigUtil.initialize() } returns Unit

        listener = Robolectric.buildService(NotificationListener::class.java).get()
        // 코루틴이 즉시 동기 실행되도록 dispatcher 교체.
        listener.serviceScope = CoroutineScope(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `TMap (KU) 알림 수신 시 ShareWorker startShare 가 호출된다`() {
        val sbn = buildSbn(
            packageName = "com.skt.tmap.ku",
            title = "경로주행",
            text = "내 위치 > 강남역",
        )

        listener.onNotificationPosted(sbn)

        verify(exactly = 1) {
            ShareWorker.startShare(
                any(),
                eq("com.skt.tmap.ku"),
                eq("경로주행"),
                eq("내 위치 > 강남역"),
            )
        }
    }

    @Test
    fun `TMap (SK) 알림 수신 시 ShareWorker startShare 가 호출된다`() {
        val sbn = buildSbn(
            packageName = "com.skt.skaf.l001mtm091",
            title = "경로주행",
            text = "내 위치 > 송파구청",
        )

        listener.onNotificationPosted(sbn)

        verify(exactly = 1) {
            ShareWorker.startShare(
                any(),
                eq("com.skt.skaf.l001mtm091"),
                eq("경로주행"),
                eq("내 위치 > 송파구청"),
            )
        }
    }

    @Test
    fun `KakaoNavi 알림 수신 시 ShareWorker startShare 가 호출된다`() {
        val sbn = buildSbn(
            packageName = "com.locnall.KimGiSa",
            title = "길안내 주행 중",
            text = "목적지 : 송파구청",
        )

        listener.onNotificationPosted(sbn)

        verify(exactly = 1) {
            ShareWorker.startShare(
                any(),
                eq("com.locnall.KimGiSa"),
                eq("길안내 주행 중"),
                eq("목적지 : 송파구청"),
            )
        }
    }

    @Test
    fun `지원하지 않는 패키지의 알림은 ShareWorker 를 호출하지 않는다`() {
        val sbn = buildSbn(
            packageName = "com.google.android.apps.maps",
            title = "Driving",
            text = "Continue ahead",
        )

        listener.onNotificationPosted(sbn)

        verify(exactly = 0) {
            ShareWorker.startShare(any(), any(), any(), any())
        }
    }

    private fun buildSbn(
        packageName: String,
        title: String,
        text: String,
    ): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putString(Notification.EXTRA_TITLE, title)
                putString(Notification.EXTRA_TEXT, text)
            }
        }
        return StatusBarNotification(
            /* pkg = */ packageName,
            /* opPkg = */ packageName,
            /* id = */ 1,
            /* tag = */ "tag",
            /* uid = */ 0,
            /* initialPid = */ 0,
            /* score = */ 0,
            /* notification = */ notification,
            /* user = */ UserHandle.getUserHandleForUid(0),
            /* postTime = */ System.currentTimeMillis(),
        )
    }
}
