package cn.edu.ubaa.ui.screens.sport

import cn.edu.ubaa.api.feature.CgyyApi
import cn.edu.ubaa.api.storage.PreSelectionStore
import cn.edu.ubaa.api.storage.PriorityOption
import cn.edu.ubaa.model.dto.CgyyBuddyDto
import cn.edu.ubaa.model.dto.CgyyBuddyListResponse
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyySlotStatusDto
import cn.edu.ubaa.model.dto.CgyySpaceAvailabilityDto
import cn.edu.ubaa.model.dto.CgyyTimeSlotDto
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * 抢场引擎回归测试：重点覆盖「反复弹验证码直到上限」bug 的修复—— dayInfo 刷新重建状态时必须保留 failCount / 服务端已确认的
 * isTaken，失败分类要能识别验证码/风控。
 */
class SportGrabViewModelTest {

  private val timeSlot =
      CgyyTimeSlotDto(id = 100, beginTime = "10:00", endTime = "12:00", label = "10:00-12:00")

  private fun dayInfoWith(slot: CgyySlotStatusDto) =
      CgyyDayInfoResponse(
          venueSiteId = 1,
          reservationDate = "2026-08-30",
          timeSlots = listOf(timeSlot),
          spaces =
              listOf(
                  CgyySpaceAvailabilityDto(
                      spaceId = 10,
                      spaceName = "A",
                      venueSiteId = 1,
                      slots = listOf(slot),
                  )
              ),
      )

  private fun option() =
      PriorityOption(spaceId = 10, spaceLabel = "A", timeLabel = "10:00", displayLabel = "A 10:00")

  private fun status(
      isTaken: Boolean = false,
      failCount: Int = 0,
  ) =
      GrabOptionStatus(
          index = 0,
          spaceId = 10,
          timeLabel = "10:00",
          displayLabel = "A 10:00",
          resolvedTimeId = 100,
          isReservable = true,
          isTaken = isTaken,
          failCount = failCount,
      )

  @Test
  fun `stale dayInfo cannot resurrect a server-confirmed taken slot`() {
    // 服务端已确认被占（submit 返回"已被预约"→ handleSubmitFailure 标记 isTaken=true, failCount=1）；
    // 但下一次 dayInfo 刷新仍显示可抢（旧快照/缓存未及时更新）。
    val freshAvailable =
        dayInfoWith(CgyySlotStatusDto(timeId = 100, reservationStatus = 1, isReservable = true))
    val prev = mapOf(0 to status(isTaken = true, failCount = 1))

    val rebuilt =
        SportGrabViewModel.buildGrabStatuses(
            freshAvailable,
            listOf(option()),
            prev,
            activeIndex = -1,
        )

    // 服务端判定必须保留 → 该意向不会再被自动重选 → 无限弹验证码的循环被打破
    assertTrue(rebuilt[0].isTaken)
    assertEquals(1, rebuilt[0].failCount)
    assertFalse(rebuilt[0].isReservable)
  }

  @Test
  fun `failCount is preserved across dayInfo refreshes`() {
    val freshAvailable =
        dayInfoWith(CgyySlotStatusDto(timeId = 100, reservationStatus = 1, isReservable = true))
    val prev = mapOf(0 to status(failCount = 1))

    val rebuilt =
        SportGrabViewModel.buildGrabStatuses(
            freshAvailable,
            listOf(option()),
            prev,
            activeIndex = -1,
        )

    // handleSubmitFailure 负责 +1（连续失败累计）；buildGrabStatuses 必须保留，否则第 2 次后永远无法判为不可用
    assertEquals(1, rebuilt[0].failCount)
  }

  @Test
  fun `fresh available slot stays pickable when no prior failure`() {
    val freshAvailable =
        dayInfoWith(CgyySlotStatusDto(timeId = 100, reservationStatus = 1, isReservable = true))

    val rebuilt =
        SportGrabViewModel.buildGrabStatuses(
            freshAvailable,
            listOf(option()),
            emptyMap(),
            activeIndex = -1,
        )

    assertTrue(rebuilt[0].isReservable)
    assertFalse(rebuilt[0].isTaken)
    assertEquals(0, rebuilt[0].failCount)
  }

  @Test
  fun `classifySubmitFailure detects captcha and rate-limit messages`() {
    assertEquals(SubmitFailureKind.CAPTCHA_ERROR, SportGrabViewModel.classifySubmitFailure("验证码错误"))
    assertEquals(
        SubmitFailureKind.CAPTCHA_ERROR,
        SportGrabViewModel.classifySubmitFailure("操作频繁，请稍后再试"),
    )
    assertEquals(
        SubmitFailureKind.CAPTCHA_ERROR,
        SportGrabViewModel.classifySubmitFailure("验证码使用次数已达上限"),
    )
  }

  @Test
  fun `classifySubmitFailure separates taken transient and unknown`() {
    assertEquals(SubmitFailureKind.TAKEN, SportGrabViewModel.classifySubmitFailure("该时段已被预约"))
    assertEquals(SubmitFailureKind.TAKEN, SportGrabViewModel.classifySubmitFailure("场地已被他人预约"))
    assertEquals(SubmitFailureKind.TRANSIENT, SportGrabViewModel.classifySubmitFailure("网络连接超时"))
    assertEquals(
        SubmitFailureKind.TRANSIENT,
        SportGrabViewModel.classifySubmitFailure("系统繁忙，请稍后重试"),
    )
    assertEquals(SubmitFailureKind.UNKNOWN, SportGrabViewModel.classifySubmitFailure("发生未知错误"))
  }

  @Test
  fun `generic retry wording no longer misclassified as transient`() {
    // 回归：此前「请稍后重试」会被判成 TRANSIENT → 对同一意向无限重拉验证码（死循环到上限）。
    // 收紧后：仅业务失败文案（无 taken/网络强信号）→ UNKNOWN（降级下一意向），不再重试同一意向。
    assertEquals(
        SubmitFailureKind.UNKNOWN,
        SportGrabViewModel.classifySubmitFailure("预约失败，请稍后重试"),
    )
    assertEquals(
        SubmitFailureKind.UNKNOWN,
        SportGrabViewModel.classifySubmitFailure("操作失败，请稍后再试"),
    )
    // 被抢文案即使带「请稍后重试」也应优先判 TAKEN
    assertEquals(
        SubmitFailureKind.TAKEN,
        SportGrabViewModel.classifySubmitFailure("该时段已被预约，请稍后重试"),
    )
    // 强网络信号仍是 TRANSIENT
    assertEquals(
        SubmitFailureKind.TRANSIENT,
        SportGrabViewModel.classifySubmitFailure("网络异常，无法连接服务器"),
    )
  }

  // ===================== 同伴 =====================

  private fun site() =
      CgyyVenueSiteDto(id = 4, siteName = "二层", venueName = "老主楼研讨室", campusName = "学院路校区")

  private class FakeCgyyApi : CgyyApi() {
    override suspend fun getBuddies(
        page: Int,
        size: Int,
    ): Result<CgyyBuddyListResponse> =
        Result.success(
            CgyyBuddyListResponse(
                content =
                    listOf(
                        CgyyBuddyDto(id = 1, name = "甲", buddyType = 1),
                        CgyyBuddyDto(id = 2, name = "乙", buddyType = 1),
                        CgyyBuddyDto(id = 3, name = "丙", buddyType = 2),
                    ),
                totalElements = 3,
            )
        )

    override suspend fun addBuddy(
        userUid: String,
        buddyType: Int,
    ): Result<CgyyBuddyListResponse> = getBuddies(0, 20)

    override suspend fun deleteBuddy(buddyId: Int): Result<CgyyBuddyListResponse> =
        getBuddies(0, 20)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun setMain(scheduler: TestCoroutineScheduler) {
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `load grab buddies populates list from api`() = runTest {
    setMain(testScheduler)
    val vm = SportGrabViewModel(FakeCgyyApi())
    vm.newDraft(site())
    advanceUntilIdle()

    assertEquals(listOf(1, 2, 3), vm.uiState.value.grabBuddies.map { it.id })
    assertFalse(vm.uiState.value.isGrabBuddiesLoading)
  }

  @Test
  fun `toggle grab buddy adds removes and caps at two`() = runTest {
    setMain(testScheduler)
    val vm = SportGrabViewModel(FakeCgyyApi())
    vm.newDraft(site())
    advanceUntilIdle()

    vm.toggleGrabBuddy(1)
    assertEquals(listOf(1), vm.uiState.value.editingDraft?.buddyIds)
    vm.toggleGrabBuddy(2)
    assertEquals(listOf(1, 2), vm.uiState.value.editingDraft?.buddyIds)
    // 超上限（含本人最多 3 人）→ 拒绝第 3 位
    vm.toggleGrabBuddy(3)
    assertEquals(listOf(1, 2), vm.uiState.value.editingDraft?.buddyIds)
    assertTrue(vm.uiState.value.message.orEmpty().contains("最多"))
    // 取消勾选
    vm.toggleGrabBuddy(1)
    assertEquals(listOf(2), vm.uiState.value.editingDraft?.buddyIds)
  }

  @Test
  fun `save draft persists selected buddies`() = runTest {
    setMain(testScheduler)
    val vm = SportGrabViewModel(FakeCgyyApi())
    vm.newDraft(site())
    advanceUntilIdle()
    vm.toggleOption(10, "A", "10:00")
    vm.toggleGrabBuddy(1)
    vm.toggleGrabBuddy(3)
    val id = vm.uiState.value.editingDraft?.id ?: error("no editing draft")
    vm.saveDraft()

    val saved = PreSelectionStore.get(id)
    assertNotNull(saved)
    assertEquals(listOf(1, 3), saved.buddyIds)
    PreSelectionStore.delete(id)
  }
}
