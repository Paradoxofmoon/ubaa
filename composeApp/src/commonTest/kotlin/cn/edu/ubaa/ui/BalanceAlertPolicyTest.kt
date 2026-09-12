package cn.edu.ubaa.ui

import cn.edu.ubaa.api.balance.BalanceAlertPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalanceAlertPolicyTest {

  @Test
  fun `no threshold never alerts`() {
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 5.0,
            thresholdYuan = null,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 0.0,
            thresholdYuan = null,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
  }

  @Test
  fun `balance parse failure never alerts`() {
    // 解析失败(null)不能当 0 元误报
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = null,
            thresholdYuan = 10.0,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
  }

  @Test
  fun `balance above or equal threshold no alert`() {
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 10.0,
            thresholdYuan = 10.0,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 20.0,
            thresholdYuan = 10.0,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
  }

  @Test
  fun `balance below threshold alerts once per day`() {
    assertTrue(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 5.0,
            thresholdYuan = 10.0,
            lastAlertDate = null,
            today = "2026-01-01",
        )
    )
    // 同一天不重复
    assertFalse(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 5.0,
            thresholdYuan = 10.0,
            lastAlertDate = "2026-01-01",
            today = "2026-01-01",
        )
    )
    // 第二天重新允许
    assertTrue(
        BalanceAlertPolicy.shouldAlert(
            balanceYuan = 5.0,
            thresholdYuan = 10.0,
            lastAlertDate = "2026-01-01",
            today = "2026-01-02",
        )
    )
  }

  @Test
  fun `should fetch respects alert and dismiss dedup`() {
    // 今天已自动提醒过 → 不查
    assertFalse(
        BalanceAlertPolicy.shouldFetch(
            today = "2026-01-01",
            lastAlertDate = "2026-01-01",
            dismissDate = null,
        )
    )
    // 今天已手动关闭 → 不查
    assertFalse(
        BalanceAlertPolicy.shouldFetch(
            today = "2026-01-01",
            lastAlertDate = null,
            dismissDate = "2026-01-01",
        )
    )
    // 昨天提醒过/关闭过 → 今天查
    assertTrue(
        BalanceAlertPolicy.shouldFetch(
            today = "2026-01-02",
            lastAlertDate = "2026-01-01",
            dismissDate = "2026-01-01",
        )
    )
  }
}
