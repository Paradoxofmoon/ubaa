package cn.edu.ubaa.model.dto

import kotlin.test.Test
import kotlin.test.assertEquals

class CgyyOrderDateDisplayTest {
  @Test
  fun `reservation date detail takes precedence`() {
    val order =
        CgyyOrderDto(
            id = 1,
            reservationDateDetail = "2026-04-01 14:00-15:35",
            reservationStartDate = "2026-04-01 14:00",
            reservationEndDate = "2026-04-01 15:35",
        )

    assertEquals("2026-04-01 14:00-15:35", order.displayReservationDateText())
  }

  @Test
  fun `reservation date is prefixed when detail only contains room and time`() {
    val order =
        CgyyOrderDto(
            id = 1,
            reservationDate = "2026-04-01",
            reservationDateDetail = "主206 09:50-12:15,12:30-13:30",
            venueSpaceName = "主206",
        )

    assertEquals("2026-04-01 09:50-12:15,12:30-13:30", order.displayReservationDateText())
  }

  @Test
  fun `same day reservation range compresses end date`() {
    val order =
        CgyyOrderDto(
            id = 1,
            reservationStartDate = "2026-04-01 14:00",
            reservationEndDate = "2026-04-01 15:35",
        )

    assertEquals("2026-04-01 14:00 - 15:35", order.displayReservationDateText())
  }

  @Test
  fun `cross day reservation range keeps both datetimes`() {
    val order =
        CgyyOrderDto(
            id = 1,
            reservationStartDate = "2026-04-01 23:00",
            reservationEndDate = "2026-04-02 00:30",
        )

    assertEquals("2026-04-01 23:00 - 2026-04-02 00:30", order.displayReservationDateText())
  }

  @Test
  fun `single start date falls back to start datetime`() {
    val order = CgyyOrderDto(id = 1, reservationStartDate = "2026-04-01 14:00")

    assertEquals("2026-04-01 14:00", order.displayReservationDateText())
  }

  @Test
  fun `plain reservation date is used when detailed fields are absent`() {
    val order = CgyyOrderDto(id = 1, reservationDate = "2026-04-01")

    assertEquals("2026-04-01", order.displayReservationDateText())
  }

  @Test
  fun `missing date fields fall back to placeholder`() {
    val order = CgyyOrderDto(id = 1)

    assertEquals("日期待定", order.displayReservationDateText())
  }
}
