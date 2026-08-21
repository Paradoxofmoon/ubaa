package cn.edu.ubaa.model.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibBookBookingStatusTest {
  @Test
  fun `ended booking cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "8", statusName = "已结束")

    assertFalse(booking.canCancelBooking())
    assertEquals("该预约已结束或已取消，无需取消", booking.cancelBlockedMessage())
  }

  @Test
  fun `user cancelled booking cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "6", statusName = "用户取消")

    assertFalse(booking.canCancelBooking())
    assertEquals("该预约已结束或已取消，无需取消", booking.cancelBlockedMessage())
  }

  @Test
  fun `active booking with id can be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "1", statusName = "已预约")

    assertTrue(booking.canCancelBooking())
    assertEquals(null, booking.cancelBlockedMessage())
  }

  @Test
  fun `booking without id cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "", status = "1", statusName = "已预约")

    assertFalse(booking.canCancelBooking())
    assertEquals("预约记录不存在或已失效，请刷新后重试", booking.cancelBlockedMessage())
  }
}
