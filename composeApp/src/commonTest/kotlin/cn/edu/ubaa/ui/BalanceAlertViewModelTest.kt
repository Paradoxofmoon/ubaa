package cn.edu.ubaa.ui

import cn.edu.ubaa.api.balance.BalanceAlertStorage
import cn.edu.ubaa.api.feature.CardApi
import cn.edu.ubaa.model.dto.CardBalanceData
import cn.edu.ubaa.ui.screens.balance.BalanceAlertViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceAlertViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val today = "2026-09-09"

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class FakeCardApi(var balance: String? = null) : CardApi() {
    var calls = 0

    override suspend fun getBalance(): Result<CardBalanceData> {
      calls++
      val b = balance
      return if (b != null) {
        Result.success(CardBalanceData(balance = b))
      } else {
        Result.failure(RuntimeException("网络不可用"))
      }
    }
  }

  private class FakeStorage : BalanceAlertStorage {
    var threshold: Double? = null
    var lastAlert: String? = null
    var dismiss: String? = null

    override fun getThresholdYuan(): Double? = threshold

    override fun setThresholdYuan(value: Double?) {
      threshold = value
    }

    override fun getLastAlertDate(): String? = lastAlert

    override fun markAlerted(date: String) {
      lastAlert = date
    }

    override fun getDismissDate(): String? = dismiss

    override fun markDismissed(date: String) {
      dismiss = date
    }
  }

  @Test
  fun `low balance below threshold shows banner and marks alerted once per day`() =
      runTest(dispatcher) {
        val api = FakeCardApi(balance = "5.00")
        val storage = FakeStorage().apply { threshold = 10.0 }
        val vm = BalanceAlertViewModel(cardApi = api, storage = storage, todayProvider = { today })

        vm.checkOnHomeEntered()
        advanceUntilIdle()

        assertTrue(vm.state.value.bannerVisible)
        assertEquals(5.0, vm.state.value.bannerBalanceYuan)
        assertEquals(today, storage.lastAlert)
        assertEquals(1, api.calls)

        // 同一天再次进入首页：不再查询、横幅保持
        vm.checkOnHomeEntered()
        advanceUntilIdle()
        assertEquals(1, api.calls)
        assertTrue(vm.state.value.bannerVisible)

        // 用户关闭后当天不再显示
        vm.dismissBanner()
        assertFalse(vm.state.value.bannerVisible)
        assertEquals(today, storage.dismiss)
        vm.checkOnHomeEntered()
        advanceUntilIdle()
        assertEquals(1, api.calls)
        assertFalse(vm.state.value.bannerVisible)
      }

  @Test
  fun `balance parse failure does not trigger banner`() =
      runTest(dispatcher) {
        val api = FakeCardApi(balance = "abc")
        val storage = FakeStorage().apply { threshold = 10.0 }
        val vm = BalanceAlertViewModel(cardApi = api, storage = storage, todayProvider = { today })

        vm.checkOnHomeEntered()
        advanceUntilIdle()

        assertFalse(vm.state.value.bannerVisible)
        assertNull(storage.lastAlert)
        assertEquals(1, api.calls)
      }

  @Test
  fun `api failure is silent`() =
      runTest(dispatcher) {
        val api = FakeCardApi(balance = null)
        val storage = FakeStorage().apply { threshold = 10.0 }
        val vm = BalanceAlertViewModel(cardApi = api, storage = storage, todayProvider = { today })

        vm.checkOnHomeEntered()
        advanceUntilIdle()

        assertFalse(vm.state.value.bannerVisible)
        assertNull(storage.lastAlert)
      }

  @Test
  fun `no threshold skips fetch entirely`() =
      runTest(dispatcher) {
        val api = FakeCardApi(balance = "1.00")
        val storage = FakeStorage() // threshold = null
        val vm = BalanceAlertViewModel(cardApi = api, storage = storage, todayProvider = { today })

        vm.checkOnHomeEntered()
        advanceUntilIdle()

        assertEquals(0, api.calls)
        assertFalse(vm.state.value.bannerVisible)
      }

  @Test
  fun `setting threshold clears banner`() =
      runTest(dispatcher) {
        val api = FakeCardApi(balance = "5.00")
        val storage = FakeStorage().apply { threshold = 10.0 }
        val vm = BalanceAlertViewModel(cardApi = api, storage = storage, todayProvider = { today })

        vm.checkOnHomeEntered()
        advanceUntilIdle()
        assertTrue(vm.state.value.bannerVisible)

        vm.setThresholdYuan(null)
        assertFalse(vm.state.value.bannerVisible)
        assertNull(storage.threshold)
      }
}
