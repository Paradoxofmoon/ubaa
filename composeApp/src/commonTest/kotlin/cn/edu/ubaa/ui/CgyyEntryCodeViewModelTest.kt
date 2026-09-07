package cn.edu.ubaa.ui

import cn.edu.ubaa.api.feature.CgyyApi
import cn.edu.ubaa.model.dto.CgyyEntryCodeView
import cn.edu.ubaa.model.dto.CgyyEntryOrderView
import cn.edu.ubaa.ui.screens.cgyy.CgyyEntryCodeViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CgyyEntryCodeViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `init loads entry code and decodes qr image`() =
      runTest(dispatcher) {
        // 动态码 + 已过期 dueDate：到期立即重拉，第二次返回静态码 → 倒计时循环终止，避免虚拟时间死循环
        var calls = 0
        val api =
            object : CgyyApi() {
              override suspend fun getVenueEntryCode(): Result<CgyyEntryCodeView> {
                calls++
                val base =
                    CgyyEntryCodeView(
                        qrCode = "aGVsbG8=",
                        isDynamicCode = true,
                        dueDate = "2000-01-01 12:00:00",
                        currDate = "2026-09-09",
                        orderView =
                            CgyyEntryOrderView(
                                campusName = "学院路校区",
                                venueName = "羽毛球馆",
                                siteName = "1号场",
                                reservationDate = "2026-09-09",
                                reservationDateDetail = "09:00-10:00",
                            ),
                    )
                return Result.success(if (calls == 1) base else base.copy(isDynamicCode = false))
              }
            }
        val vm = CgyyEntryCodeViewModel(api)
        advanceUntilIdle()

        assertEquals(2, calls)
        assertNotNull(vm.uiState.value.code)
        assertEquals("羽毛球馆", vm.uiState.value.code?.orderView?.venueName)
        assertEquals(false, vm.uiState.value.code?.isDynamicCode)
        assertNull(vm.uiState.value.error)
      }

  @Test
  fun `load failure exposes error message`() =
      runTest(dispatcher) {
        val api =
            object : CgyyApi() {
              override suspend fun getVenueEntryCode() =
                  Result.failure<CgyyEntryCodeView>(RuntimeException("网络不可用"))
            }
        val vm = CgyyEntryCodeViewModel(api)
        advanceUntilIdle()

        assertEquals("网络不可用", vm.uiState.value.error)
        assertNull(vm.uiState.value.code)
      }
}
