package cn.edu.ubaa.model.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CgyyOrderStatusTest {
  @Test
  fun `cancelled order resolves to cancelled display`() {
    val status = CgyyOrderDto(id = 1, orderStatus = 2).displayStatus()

    assertEquals("已取消", status.primaryText)
    assertFalse(status.isCancelable)
  }

  @Test
  fun `approved order resolves to approved display`() {
    val status = CgyyOrderDto(id = 1, orderStatus = 1, checkStatus = 1).displayStatus()

    assertEquals("审批通过", status.primaryText)
    assertEquals("审批通过", status.detailText)
    assertTrue(status.isCancelable)
  }

  @Test
  fun `pending order resolves to generic and detailed approval text`() {
    val status = CgyyOrderDto(id = 1, orderStatus = 1, checkStatus = 4).displayStatus()

    assertEquals("待审批", status.primaryText)
    assertEquals("待宣传部审批", status.detailText)
    assertTrue(status.isCancelable)
  }

  @Test
  fun `rejected order resolves to rejected display`() {
    val status = CgyyOrderDto(id = 1, orderStatus = 1, checkStatus = -3).displayStatus()

    assertEquals("副书记/副处长审批驳回", status.primaryText)
    assertEquals("副书记/副处长审批驳回", status.detailText)
    assertFalse(status.isCancelable)
  }

  @Test
  fun `unknown order falls back to unknown label`() {
    val status = CgyyOrderDto(id = 1, orderStatus = 9).displayStatus()

    assertEquals("未知(9)", status.primaryText)
    assertFalse(status.isCancelable)
  }
}
