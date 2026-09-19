package cn.edu.ubaa.api

import cn.edu.ubaa.api.local.extractCashierUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** cc-pay 收银台地址解析单测：覆盖直连 / backUrl / 整体编码 / 部分编码 / 双重编码各形态。 */
class CcpayCashierUrlTest {

  @Test
  fun `plain cashier url returns as-is`() {
    assertEquals(
        "https://cashier.cc-pay.cn/cashier?id=abc123&payWayId=wx",
        extractCashierUrl("https://cashier.cc-pay.cn/cashier?id=abc123&payWayId=wx"),
    )
  }

  @Test
  fun `pass login with backUrl decodes cashier url`() {
    assertEquals(
        "https://cashier.cc-pay.cn/cashier?id=abc123",
        extractCashierUrl(
            "https://pass.cc-pay.cn/login?backUrl=https%3A%2F%2Fcashier.cc-pay.cn%2Fcashier%3Fid%3Dabc123"
        ),
    )
  }

  @Test
  fun `partially encoded cashier url is fully decoded`() {
    // %3F=%3D%26 残留会让 WebView 加载失败（把查询串当成路径），必须完整解码
    assertEquals(
        "https://cashier.cc-pay.cn/cashier?id=abc123&payWayId=wx",
        extractCashierUrl("https://cashier.cc-pay.cn/cashier%3Fid%3Dabc123%26payWayId%3Dwx"),
    )
  }

  @Test
  fun `fully encoded cashier url is decoded first`() {
    assertEquals(
        "https://cashier.cc-pay.cn/cashier?id=abc123",
        extractCashierUrl("https%3A%2F%2Fcashier.cc-pay.cn%2Fcashier%3Fid%3Dabc123"),
    )
  }

  @Test
  fun `double encoded backUrl is decoded twice`() {
    assertEquals(
        "https://cashier.cc-pay.cn/cashier?id=abc123",
        extractCashierUrl(
            "https://pass.cc-pay.cn/login?backUrl=https%253A%252F%252Fcashier.cc-pay.cn%252Fcashier%253Fid%253Dabc123"
        ),
    )
  }

  @Test
  fun `blank or unrecognizable returns null`() {
    assertNull(extractCashierUrl(null))
    assertNull(extractCashierUrl(""))
    assertNull(extractCashierUrl("   "))
    assertNull(extractCashierUrl("下单失败，请稍后重试"))
    assertNull(extractCashierUrl("https://example.com/somewhere"))
  }
}
