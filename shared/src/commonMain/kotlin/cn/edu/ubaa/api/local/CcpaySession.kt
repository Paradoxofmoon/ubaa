package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.network.platformLog
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/**
 * 供 composeApp 电费缴费等功能复用的 cc-pay 会话与收银台工具。
 *
 * 背景：电费(shsd.buaa.edu.cn)下单返回的 payUrl 是 `https://pass.cc-pay.cn/login?backUrl=<url-encoded cashier
 * 地址>`， 跳过去还需登录 cc-pay。而校园卡充值已通过 CAS SSO 建立了 cc-pay 会话。 本文件把「建立 cc-pay 会话」和「从 payUrl 解析收银台地址」提升为
 * public， 让电费等其它功能复用同一套 session 与隐藏 WebView 唤起支付方案，避免重复登录。
 */

/**
 * 建立 pass/mall/cashier.cc-pay.cn 会话（与校园卡共用同一套 CAS SSO 跳转逻辑）。 复用
 * [LocalUpstreamClientProvider.shared] 共享 client，cookie 自动落入 [LocalCookieStore]， 之后
 * [buildCcpayCookieHeader] 即可取到 cc-pay 域 cookie 注入隐藏 WebView。
 */
suspend fun ensureCcpaySession() {
  val client = LocalUpstreamClientProvider.shared()
  // pass 登录（建立 .cc-pay.cn 全局会话）
  val r1 =
      client.get(
          localUpstreamUrl(
              "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fpass.cc-pay.cn%2Flogin"
          )
      ) {
        header(
            HttpHeaders.Accept,
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
      }
  platformLog("CCPAY", "CAS跳转 pass: status=${r1.status}")
  // mall 登录（充值入口在 mall，需独立建立会话）
  val r2 =
      client.get(
          localUpstreamUrl(
              "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fmall.cc-pay.cn%2Flogin"
          )
      ) {
        header(
            HttpHeaders.Accept,
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
      }
  platformLog("CCPAY", "CAS跳转 mall: status=${r2.status} url=${r2.call.request.url}")
  // 触达 mall / cashier
  val r3 =
      client.get(localUpstreamUrl("https://mall.cc-pay.cn/api/address")) {
        header(HttpHeaders.Accept, "application/json")
      }
  platformLog("CCPAY", "mall触达: status=${r3.status} body=${r3.bodyAsText().take(120)}")
  val r4 =
      client.get(localUpstreamUrl("https://cashier.cc-pay.cn/api/address")) {
        header(HttpHeaders.Accept, "application/json")
      }
  platformLog("CCPAY", "cashier触达: status=${r4.status} body=${r4.bodyAsText().take(120)}")
}

/**
 * 从电费/校网下单返回的 payUrl 中解析出真正的 cc-pay 收银台地址。
 *
 * 支持多种形态：
 * 1. `https://pass.cc-pay.cn/login?backUrl=<url-encoded cashier 地址>` → 解码 backUrl 得 cashier 地址
 * 2. 直接就是 `https://cashier.cc-pay.cn/cashier?id=xxx` → 原样返回
 * 3. 整体百分号编码（`https%3A%2F%2Fcashier.cc-pay.cn%2F...`）→ 先完整解码再识别
 * 4. 部分编码（`https://cashier.cc-pay.cn/cashier%3Fid%3Dxxx`）或 backUrl 双重编码 → 多轮解码
 *
 * 返回 null 表示无法识别为 cc-pay 收银台地址。
 */
fun extractCashierUrl(payUrl: String?): String? {
  if (payUrl.isNullOrBlank()) return null
  var trimmed = payUrl.trim()

  // 形态 3：整体百分号编码（scheme 也被编码），先完整解码再识别
  if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.contains('%')) {
    val decoded = percentDecode(trimmed)
    if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
      trimmed = decoded
    }
  }

  val cashier =
      when {
        // 形态 2/4：直接就是收银台地址（可能含未解码的 %3F/%3D/%26，最后统一处理）
        trimmed.contains("cashier.cc-pay.cn") -> {
          val back = extractBackUrlParam(trimmed)
          when {
            back?.contains("cashier.cc-pay.cn") == true -> back
            trimmed.startsWith("https://cashier.cc-pay.cn") ||
                trimmed.startsWith("http://cashier.cc-pay.cn") -> trimmed
            else -> null
          }
        }
        // 形态 1：pass.cc-pay.cn/login?backUrl=<encoded cashier>
        else -> extractBackUrlParam(trimmed)?.takeIf { it.contains("cashier.cc-pay.cn") }
      } ?: return null

  // 最终结果做一次完整百分号解码，抹掉 %3F/%3D/%26 等残留，
  // 避免 "cashier%3Fid%3Dxxx" 这类坏地址进 WebView 导致支付页加载失败、无法唤起支付 App。
  val decoded = percentDecode(cashier)
  return decoded.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

/** 从 URL 的 backUrl 查询参数解码出原始值（兼容一次/两次编码）。 */
private fun extractBackUrlParam(url: String): String? {
  val markers = listOf("backUrl=", "backurl=", "back_url=")
  for (marker in markers) {
    val idx = url.indexOf(marker, ignoreCase = true)
    if (idx < 0) continue
    val valueStart = idx + marker.length
    var end = valueStart
    while (end < url.length && url[end] != '&' && url[end] != '#') end++
    val raw = url.substring(valueStart, end)
    if (raw.isBlank()) continue
    // backUrl 值是 URL 编码的（如 %3A%2F%2F -> ://）。个别服务端整体编码后再内嵌，解码两轮兼容。
    var decoded = raw
    repeat(2) { decoded = percentDecode(decoded) }
    return when {
      decoded.contains("http://") || decoded.contains("https://") -> decoded
      raw.contains("http://") || raw.contains("https://") -> raw
      else -> null
    }
  }
  return null
}

/** 极简 URL 百分号解码（仅需处理 backUrl 中的 %XX，无第三方依赖）。 */
private fun percentDecode(s: String): String {
  val sb = StringBuilder(s.length)
  var i = 0
  while (i < s.length) {
    val c = s[i]
    if (c == '%' && i + 2 < s.length) {
      val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
      if (hex != null) {
        sb.append(hex.toChar())
        i += 3
        continue
      }
    }
    sb.append(c)
    i++
  }
  return sb.toString()
}
