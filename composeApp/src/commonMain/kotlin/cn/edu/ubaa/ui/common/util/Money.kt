package cn.edu.ubaa.ui.common.util

/**
 * 跨平台金额格式化：固定两位小数（如 "12.34"）。
 *
 * iOS/JS 目标没有 java.lang.String.format，不能用 `"%.2f".format(...)`，必须用纯 Kotlin 实现。
 * 只格式化数值本身，货币符号由调用方拼接（如 "¥12.34" / "¥ 12.34"）。
 */
fun formatMoney(value: Double): String {
  val cents = kotlin.math.round(kotlin.math.abs(value) * 100).toLong()
  val sign = if (value < 0) "-" else ""
  return "$sign${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
