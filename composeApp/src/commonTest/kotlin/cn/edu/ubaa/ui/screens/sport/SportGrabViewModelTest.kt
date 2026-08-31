package cn.edu.ubaa.ui.screens.sport

import cn.edu.ubaa.api.storage.PriorityOption
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyySlotStatusDto
import cn.edu.ubaa.model.dto.CgyySpaceAvailabilityDto
import cn.edu.ubaa.model.dto.CgyyTimeSlotDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
