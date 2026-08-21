package cn.edu.ubaa.ui

import cn.edu.ubaa.model.dto.CgyyOrderDto
import cn.edu.ubaa.model.dto.displayReservationDateText
import cn.edu.ubaa.ui.screens.cgyy.shouldShowCancelAction
import cn.edu.ubaa.ui.screens.cgyy.toLockCodeDisplayModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class CgyyScreenLogicTest {
  @Test
  fun `cancel action hidden for cancelled order`() {
    assertFalse(shouldShowCancelAction(CgyyOrderDto(id = 1, orderStatus = 2)))
  }

  @Test
  fun `cancel action hidden for rejected order`() {
    assertFalse(shouldShowCancelAction(CgyyOrderDto(id = 1, orderStatus = 1, checkStatus = -2)))
  }

  @Test
  fun `cancel action kept for pending order`() {
    assertTrue(shouldShowCancelAction(CgyyOrderDto(id = 1, orderStatus = 1, checkStatus = 2)))
  }

  @Test
  fun `order card date text falls back to reservation range`() {
    val order =
        CgyyOrderDto(
            id = 1,
            reservationStartDate = "2026-04-01 14:00",
            reservationEndDate = "2026-04-01 15:35",
        )

    assertEquals("2026-04-01 14:00 - 15:35", order.displayReservationDateText())
  }

  @Test
  fun `lock code parser marks current reservation as unlockable`() {
    val model =
        buildJsonObject {
              put("qrCode", "349659")
              put("dueDate", "2026-04-02 14:46:00")
              putJsonObject("orderView") {
                put("tradeNo", "D260401000054")
                put("siteName", "三号楼研讨室")
                put("venueName", "三号楼研讨室")
                put("venueSpaceName", "SH3-102")
                put("reservationDate", "2026-04-02")
                put("reservationDateDetail", "SH3-102 14:00-15:35,15:50-18:15")
                put("reservationStartDate", "2026-04-02 14:00:00")
                put("reservationEndDate", "2026-04-02 18:15:00")
              }
            }
            .toLockCodeDisplayModel()

    assertNotNull(model)
    assertTrue(model.hasLockCode)
    assertTrue(model.hasUpcomingReservation)
    assertEquals("14:00-15:35,15:50-18:15", model.timeRangeText)
  }

  @Test
  fun `lock code parser keeps future reservation info when qrCode is absent`() {
    val model =
        buildJsonObject {
              put("qrCode", "null")
              put("dueDate", "null")
              putJsonObject("orderView") {
                put("tradeNo", "D260401000115")
                put("siteName", "三号楼研讨室")
                put("venueName", "三号楼研讨室")
                put("venueSpaceName", "SH3-104")
                put("reservationDate", "2026-04-05")
                put("reservationDateDetail", "SH3-104 14:00-15:35,15:50-18:15")
                put("reservationStartDate", "2026-04-05 14:00:00")
                put("reservationEndDate", "2026-04-05 18:15:00")
              }
            }
            .toLockCodeDisplayModel()

    assertNotNull(model)
    assertFalse(model.hasLockCode)
    assertTrue(model.hasUpcomingReservation)
    assertEquals("2026-04-05", model.reservationDate)
    assertEquals("14:00-15:35,15:50-18:15", model.timeRangeText)
  }
}
