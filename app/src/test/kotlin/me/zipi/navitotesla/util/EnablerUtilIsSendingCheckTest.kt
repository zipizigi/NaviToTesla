package me.zipi.navitotesla.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import me.zipi.navitotesla.db.AppDatabase
import me.zipi.navitotesla.db.ConditionDao
import me.zipi.navitotesla.db.ConditionEntity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * `EnablerUtil.isSendingCheck()` 는 사용자가 설정한 블루투스/조건 게이트로 안내 전송 여부를 결정한다.
 * 이 분기가 잘못 변경되면 의도치 않은 목적지가 차량으로 전송되거나, 반대로 정상 케이스가 전부 무시될 수 있다.
 *
 * 모든 분기를 커버:
 * 1. appEnabled = false                                            → false
 * 2. appEnabled = true, appCondition = false                       → true (조건 비활성, 무조건 통과)
 * 3. appCondition = true, 조건 목록 모두 비어있음                  → true (조건이 없으면 통과)
 * 4. 블루투스 조건 존재 + 매칭되는 블루투스 연결됨                 → true
 * 5. 블루투스 조건 존재 + 연결된 블루투스가 일치하지 않음          → false
 * 6. 블루투스 조건 존재 + 연결된 블루투스 없음                     → false
 * 7. 블루투스 매칭은 대소문자 무시 (저장은 lowercase, 조건은 자유) → true
 */
class EnablerUtilIsSendingCheckTest {
    private lateinit var dao: ConditionDao

    @Before
    fun setUp() {
        mockkStatic(FirebaseCrashlytics::class)
        every { FirebaseCrashlytics.getInstance() } returns mockk(relaxed = true)
        mockkObject(AnalysisUtil)
        every { AnalysisUtil.log(any()) } returns Unit

        mockkObject(PreferencesUtil)
        mockkObject(AppDatabase)
        dao = mockk()
        val db = mockk<AppDatabase>()
        every { AppDatabase.getInstance() } returns db
        every { db.conditionDao() } returns dao

        // 기본값 — 각 테스트에서 필요 시 덮어씀.
        coEvery { PreferencesUtil.getBoolean("appEnabled", true) } returns true
        coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns false
        coEvery { dao.findCondition("wifi") } returns emptyList()
        coEvery { dao.findCondition("bluetooth") } returns emptyList()

        clearConnectedBluetooth()
    }

    @After
    fun tearDown() {
        clearConnectedBluetooth()
        unmockkAll()
    }

    @Test
    fun `appEnabled=false 면 false`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appEnabled", true) } returns false

            assertFalse(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `appEnabled=true, appCondition=false 면 true`() =
        runTest {
            // 기본 setUp 그대로
            assertTrue(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `appCondition=true 인데 wifi-bluetooth 조건이 모두 비어있으면 true`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns true

            assertTrue(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `블루투스 조건과 일치하는 디바이스가 연결돼 있으면 true`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns true
            coEvery { dao.findCondition("bluetooth") } returns listOf(condition("MyCar"))
            EnablerUtil.addConnectedBluetooth("MyCar")

            assertTrue(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `블루투스 조건은 있지만 연결된 디바이스가 다른 이름이면 false`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns true
            coEvery { dao.findCondition("bluetooth") } returns listOf(condition("MyCar"))
            EnablerUtil.addConnectedBluetooth("OtherCar")

            assertFalse(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `블루투스 조건은 있지만 연결된 디바이스가 없으면 false`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns true
            coEvery { dao.findCondition("bluetooth") } returns listOf(condition("MyCar"))

            assertFalse(EnablerUtil.isSendingCheck())
        }

    @Test
    fun `블루투스 매칭은 대소문자를 무시한다`() =
        runTest {
            coEvery { PreferencesUtil.getBoolean("appCondition", false) } returns true
            coEvery { dao.findCondition("bluetooth") } returns listOf(condition("MyCar"))
            EnablerUtil.addConnectedBluetooth("MYCAR")

            assertTrue(EnablerUtil.isSendingCheck())
        }

    private fun condition(name: String) = ConditionEntity(name = name, type = "bluetooth", created = Date())

    /**
     * `EnablerUtil.connectedBluetoothDevice` 는 object 내부의 private MutableSet 이라 테스트 간 상태가 남는다.
     * 추가했던 이름만 정확히 알기 어렵고 다른 테스트가 셋에 의존하지도 않게, 매 테스트 시작/종료 시 비운다.
     */
    private fun clearConnectedBluetooth() {
        val field = EnablerUtil::class.java.getDeclaredField("connectedBluetoothDevice")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val set = field.get(EnablerUtil) as MutableSet<String>
        synchronized(set) { set.clear() }
    }
}
