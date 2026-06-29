package me.zipi.navitotesla.ui.setting

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.zipi.navitotesla.TestApplication
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `SettingViewModel` 은 동작/조건 토글의 상태 소유 및 저장 책임을 가진다.
 *
 * 회귀 방지 핵심: **저장된 상태를 화면으로 "로드"하는 경로는 절대 저장소에 다시 쓰면 안 된다.**
 * 과거에는 LiveData 옵저버가 초기 기본값(condition=false)을 그대로 persist 하여
 * 사용자가 켜 둔 조건이 화면 재진입마다 false 로 덮어써졌다(= 리뷰의 "계속 비활성으로 바뀜").
 * 저장은 오직 사용자가 실제로 토글을 바꿨을 때만 일어나야 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = TestApplication::class)
class SettingViewModelTest {
    private lateinit var viewModel: SettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit

        mockkObject(EnablerUtil)
        coEvery { EnablerUtil.getAppEnabled() } returns true
        coEvery { EnablerUtil.getConditionEnabled() } returns false
        coEvery { EnablerUtil.listBluetoothCondition() } returns emptyList()
        coEvery { EnablerUtil.setAppEnabled(any()) } returns Unit
        coEvery { EnablerUtil.setConditionEnabled(any()) } returns Unit

        viewModel = SettingViewModel()
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `loadStates 는 저장된 값을 반영하고 저장소에 다시 쓰지 않는다`() =
        runTest {
            coEvery { EnablerUtil.getAppEnabled() } returns false
            coEvery { EnablerUtil.getConditionEnabled() } returns true

            viewModel.loadStates()

            assertEquals(false, viewModel.isAppEnabled.value)
            assertEquals(true, viewModel.isConditionEnabled.value)
            coVerify(exactly = 0) { EnablerUtil.setAppEnabled(any()) }
            coVerify(exactly = 0) { EnablerUtil.setConditionEnabled(any()) }
        }

    @Test
    fun `사용자가 조건을 켜면 true 가 저장되고 값이 갱신된다`() =
        runTest {
            // 현재 isConditionEnabled 기본값 = false
            viewModel.setConditionEnabledByUser(true)

            assertEquals(true, viewModel.isConditionEnabled.value)
            coVerify(exactly = 1) { EnablerUtil.setConditionEnabled(true) }
        }

    @Test
    fun `현재와 같은 값으로 조건 토글을 호출하면(복원-기본값 재방출) 저장하지 않는다`() =
        runTest {
            // 현재 isConditionEnabled 기본값 = false. 복원/초기 재방출 시 같은 false 가 들어온다.
            viewModel.setConditionEnabledByUser(false)

            coVerify(exactly = 0) { EnablerUtil.setConditionEnabled(any()) }
        }

    @Test
    fun `사용자가 앱 동작을 끄면 false 가 저장되고 값이 갱신된다`() =
        runTest {
            // 현재 isAppEnabled 기본값 = true
            viewModel.setAppEnabledByUser(false)

            assertEquals(false, viewModel.isAppEnabled.value)
            coVerify(exactly = 1) { EnablerUtil.setAppEnabled(false) }
        }

    @Test
    fun `현재와 같은 값으로 앱 동작 토글을 호출하면 저장하지 않는다`() =
        runTest {
            // 현재 isAppEnabled 기본값 = true
            viewModel.setAppEnabledByUser(true)

            coVerify(exactly = 0) { EnablerUtil.setAppEnabled(any()) }
        }
}
