package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.auth.userFacingMessageForCode
import cn.edu.ubaa.api.network.platformLog
import cn.edu.ubaa.api.feature.CardApiBackend
import cn.edu.ubaa.api.feature.CardPayWay
import cn.edu.ubaa.api.feature.CardRechargeResult
import cn.edu.ubaa.model.dto.CardBalanceData
import cn.edu.ubaa.model.dto.CardBalanceResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 校园卡充值账单项（BUAA_CAMPUS_CARD_RECHARGE）
// itemId 不是硬编码，而是运行时从 /api/payment_items 按 code 匹配得到。
private const val CAMPUS_CARD_RECHARGE_CODE = "BUAA_CAMPUS_CARD_RECHARGE"
private const val CAMPUS_CARD_FEE = "campus_card_fee"

internal class LocalCardApiBackend : CardApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getBalance(): Result<CardBalanceData> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      val response =
          LocalUpstreamClientProvider.shared()
              .get(localUpstreamUrl("https://pass.cc-pay.cn/api/campus_card/balance")) {
                parameter("t", Clock.System.now().toEpochMilliseconds())
                parameter("stuNo", studentId)
                header(HttpHeaders.Accept, "application/json, text/plain, */*")
              }
      parseBalanceResponse(response)
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("一卡通余额查询失败，请稍后重试"))
    }
  }

  override suspend fun getRechargePayWays(): Result<List<CardPayWay>> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      // 手机端 App 直接支付使用已实测的移动端微信/支付宝方式。
      // /api/pay_ways 实时接口在 App 场景可能只返回数字人民币等，不可靠，
      // 仅当它明确返回微信/支付宝时才采用，否则一律用已知移动端方式。
      var useRealTime = false
      val itemId = fetchRechargeItemId()
      if (itemId.isNotBlank()) {
        val ways = fetchPayWays(itemId)
        val hasWxOrAli = ways.any { it.channel.contains("wx") || it.channel.contains("weixin") || it.channel.contains("alipay") }
        if (hasWxOrAli) {
          useRealTime = true
          return Result.success(ways)
        }
        platformLog("CR", "实时 pay_ways 无微信/支付宝，改用已知移动端方式")
      }
      if (!useRealTime) {
        val known = knownMobilePayWays()
        if (known.isNotEmpty()) return Result.success(known)
      }
      Result.failure(ApiCallException("未找到可用的移动支付方式", HttpStatusCode.BadGateway, "card_error"))
    } catch (e: Exception) {
      // 任何异常回退到已实测的移动端支付方式，保证充值可用
      val fallback = knownMobilePayWays()
      if (fallback.isNotEmpty()) Result.success(fallback) else Result.failure(e.toUserFacingApiException("获取支付方式失败，请稍后重试"))
    }
  }

  override suspend fun beginRecharge(
      amount: String,
      payWayId: String,
  ): Result<CardRechargeResult> {
    val studentId = requireStudentId() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      ensureCcpaySession()
      platformLog("CR", "会话建立完成")

      // 1. 获取校园卡充值账单项的真实 itemId（按 code 匹配，不硬编码）
      val itemId = fetchRechargeItemId()
      platformLog("CR", "账单项 itemId: $itemId")
      if (itemId.isBlank()) {
        throw ApiCallException("未找到校园卡充值缴费项", HttpStatusCode.BadGateway, "card_error")
      }
      // 2. 获取实名信息（学号 + 姓名）
      val (stuNo, realName) = fetchFeeInfo()
      platformLog("CR", "实名信息: $stuNo/$realName")
      if (stuNo.isBlank() || realName.isBlank()) {
        throw ApiCallException("获取校园卡实名信息失败", HttpStatusCode.BadGateway, "card_error")
      }
      // 3. 创建交易订单，拿到收银台交易号 + 收银台地址
      val (transactionId, cashierUrl) = createTransaction(amount, itemId, stuNo, realName)
      platformLog("CR", "交易创建完成: $transactionId  cashierUrl=$cashierUrl")

      // 返回收银台地址，用隐藏 WebView 加载真实收银台页并注入 JS 自动点支付方式唤起支付 App。
      // 不在 API 层调 initiatePay（避免把支付方式锁定在订单上）。
      Result.success(CardRechargeResult(cashierUrl = cashierUrl.ifBlank { null }))
    } catch (e: ApiCallException) {
      platformLog("CR", "充值失败(ApiCall): ${e.message} :: ${e.status}")
      Result.failure(ApiCallException("充值失败: ${e.message}", e.status ?: HttpStatusCode.BadGateway, "card_error"))
    } catch (e: Exception) {
      platformLog("CR", "充值失败: ${e.message} :: ${e::class.simpleName}")
      Result.failure(ApiCallException("充值失败: ${e.message ?: e::class.simpleName}", HttpStatusCode.BadGateway, "card_error"))
    }
  }

  // ===== 私有方法 =====

  private suspend fun requireStudentId(): String? {
    val session = LocalAuthSessionStore.get() ?: return null
    return session.user.schoolid.ifBlank { session.username }.ifBlank { null }
  }

  /** 通过 CAS SSO 跳转建立 pass.cc-pay.cn / mall.cc-pay.cn 会话。 */
  /** 从缴费项列表按 code 匹配校园卡充值项，返回真实 itemId。 */
  private suspend fun fetchRechargeItemId(): String {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://mall.cc-pay.cn/api/payment_items")
        ) {
          parameter("t", Clock.System.now().toEpochMilliseconds())
          parameter("pageSize", "-1")
          header(HttpHeaders.Accept, "application/json")
        }
    val body = response.bodyAsText()
    platformLog("CR", "fetchRechargeItemId: status=${response.status} body=${body.take(300)}")
    checkCcpaySession(response, body)
    val root = json.parseToJsonElement(body).jsonObject
    // data 可能是 { data: [ ... ] } 或直接是数组
    val dataNode = root["data"]
    val items =
        when {
          dataNode is JsonObject && dataNode["data"] != null -> dataNode["data"]
          else -> dataNode
        }
    if (items !is JsonArray) {
      platformLog("CR", "payment_items 结构异常: ${body.take(120)}")
      return ""
    }
    for (item in items) {
      val obj = item as? JsonObject ?: continue
      val code = obj["code"]?.jsonPrimitive?.contentOrNull
      val isActive = obj["isActive"]
      val isDeleted = obj["isDeleted"]
      if (code == CAMPUS_CARD_RECHARGE_CODE) {
        val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        platformLog("CR", "命中充值项 id=$id active=$isActive deleted=$isDeleted")
        return id
      }
    }
    return ""
  }

  /** 从 /api/pay_ways 获取支付方式列表（保留 web 与 mobile 区分，由调用方过滤）。 */
  private suspend fun fetchPayWays(goodsId: String): List<CardPayWay> {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://cashier.cc-pay.cn/api/pay_ways")
        ) {
          parameter("_t", "=${Clock.System.now().toEpochMilliseconds()}")
          parameter("payScene", "")
          parameter("goodsId", goodsId)
          header("version", "v2")
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header(HttpHeaders.Referrer, "https://cashier.cc-pay.cn/cashier?id=")
        }
    val body = response.bodyAsText()
    platformLog("CR", "fetchPayWays: status=${response.status} body=${body.take(500)}")
    checkCcpaySession(response, body)
    val root = json.parseToJsonElement(body).jsonObject
    val data = root["data"].safeObject() ?: return emptyList()
    val normal = data["normal"] as? JsonArray ?: return emptyList()
    val result = mutableListOf<CardPayWay>()
    for (item in normal) {
      val obj = item as? JsonObject ?: continue
      val isActive = obj["isActive"]?.jsonPrimitive?.contentOrNull ?: "true"
      if (isActive == "false") continue
      val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
      val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
      if (id.isBlank()) continue
      // channel 区分：name 以 _web 结尾的是电脑网站支付（主扫二维码），非 _web 为移动/原生方式
      val isWeb = name.endsWith("_web")
      val channel = if (isWeb) "web" else name
      result.add(CardPayWay(id = id, name = name, text = text.ifBlank { name }, channel = channel))
    }
    return result
  }

  /** 已实测/确认可用的移动端直接支付方式（App 拉起支付 App）。 */
  private fun knownMobilePayWays(): List<CardPayWay> {
    return listOf(
        CardPayWay(
            id = "5acada6148333c5f4695a68e",
            name = "wxpay",
            text = "微信支付",
            channel = "wxpay",
        ),
        CardPayWay(
            id = "5acada6048333cbc8195a68e",
            name = "alipay",
            text = "支付宝",
            channel = "alipay",
        ),
    )
  }

  /** 获取校园卡充值所需的实名信息。 */
  private suspend fun fetchFeeInfo(): Pair<String, String> {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://mall.cc-pay.cn/api/bill/note/feeInfo")
        ) {
          parameter("t", Clock.System.now().toEpochMilliseconds())
          parameter("fromType", CAMPUS_CARD_FEE)
          header(HttpHeaders.Accept, "application/json")
        }
    val body = response.bodyAsText()
    platformLog("CR", "fetchFeeInfo: status=${response.status} body=${body.take(200)}")
    checkCcpaySession(response, body)
    val data = json.parseToJsonElement(body).jsonObject["data"].safeObject()
    val stuNo = data?.get("stuNo")?.jsonPrimitive?.contentOrNull ?: ""
    val realName = data?.get("realName")?.jsonPrimitive?.contentOrNull ?: ""
    return stuNo to realName
  }

  /** 创建支付交易，返回收银台交易号（从 cashierUrl 的 id= 参数解析）。 */
  private suspend fun createTransaction(
      amount: String,
      itemId: String,
      stuNo: String,
      realName: String,
  ): Pair<String, String> {
    val feeInfoJson = json.encodeToString(
        buildJsonObject {
          put("stuNo", JsonPrimitive(stuNo))
          put("realName", JsonPrimitive(realName))
        }
    )
    val payload = json.encodeToString(
        buildJsonObject {
          put("targetId", JsonPrimitive("mall_id"))
          put("targetType", JsonPrimitive("mall"))
          put("money", JsonPrimitive(amount))
          put("itemId", JsonPrimitive(itemId))
          put("feeInfo", JsonPrimitive(feeInfoJson))
          put("fromType", JsonPrimitive(CAMPUS_CARD_FEE))
          put("choice", JsonPrimitive(""))
        }
    )
    val response =
        LocalUpstreamClientProvider.shared().post(
            localUpstreamUrl("https://mall.cc-pay.cn/api/payment")
        ) {
          parameter("t", Clock.System.now().toEpochMilliseconds())
          contentType(ContentType.Application.Json)
          setBody(payload)
          header(HttpHeaders.Accept, "application/json, application/json")
          // Referer 需带 name/cardNo/school/money 参数，服务器据此校验
          header(
              HttpHeaders.Referrer,
              "https://mall.cc-pay.cn/entry/card/$itemId?name=${realName.encodeURLParameter()}&cardNo=$stuNo&school=buaa&money=$amount"
          )
        }
    val body = response.bodyAsText()
    platformLog("CR", "createTransaction: status=${response.status} body=${body.take(600)}")
    checkCcpaySession(response, body)
    val jsonBody = json.parseToJsonElement(body).jsonObject
    val msg = jsonBody["message"]?.jsonPrimitive?.contentOrNull
    val data = jsonBody["data"].safeObject()
    val successStr = jsonBody["success"]?.jsonPrimitive?.contentOrNull
    if (data == null || successStr == "false") {
      throw ApiCallException(
          "创建充值订单失败${msg?.let { ": $it" } ?: ""}",
          HttpStatusCode.BadGateway,
          "card_error",
      )
    }
    // 交易号在 data.cashierUrl 的 ?id= 里（与 data.id 账单ID 不同）
    val cashierUrl = data["cashierUrl"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val transactionId = extractCashierTransactionId(cashierUrl)
    platformLog("CR", "cashierUrl=$cashierUrl -> transactionId=$transactionId")
    if (transactionId.isBlank()) {
      throw ApiCallException("创建充值订单失败：未返回收银台地址", HttpStatusCode.BadGateway, "card_error")
    }
    return transactionId to cashierUrl
  }

  /** 从 cashierUrl（https://cashier.cc-pay.cn/cashier?id=xxx）解析交易号。 */
  private fun extractCashierTransactionId(cashierUrl: String): String {
    if (cashierUrl.isBlank()) return ""
    val marker = "id="
    val idx = cashierUrl.indexOf(marker)
    if (idx < 0) return ""
    var end = idx + marker.length
    while (end < cashierUrl.length && cashierUrl[end] != '&' && cashierUrl[end] != '#') {
      end++
    }
    return cashierUrl.substring(idx + marker.length, end)
  }

  /** 发起支付，返回支付跳转地址。 */
  private suspend fun initiatePay(transactionId: String, payWayId: String): CardRechargeResult {
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://cashier.cc-pay.cn/transaction/pay")
        ) {
          parameter("id", transactionId)
          parameter("payWayId", payWayId)
          parameter("phoneNumber", "")
          parameter("ecCode", "")
          header("version", "v2")
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header(HttpHeaders.Referrer, "https://cashier.cc-pay.cn/cashier?id=$transactionId")
        }
    val body = response.bodyAsText()
    platformLog("CR", "initiatePay: status=${response.status} body=${body.take(400)}")
    checkCcpaySession(response, body)
    val data = json.parseToJsonElement(body).jsonObject["data"].safeObject() ?: return CardRechargeResult()
    return CardRechargeResult(
        payUrl = data["payUrl"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        payQrCode = data["payQrCode"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        payWebForm = data["payWebForm"]?.jsonPrimitive?.contentOrNull,
    )
  }

  private suspend fun checkCcpaySession(response: HttpResponse, body: String) {
    if (response.status == HttpStatusCode.Unauthorized) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
    if (localIsSsoUrl(response.call.request.url.toString())) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
    val trimmed = body.trimStart()
    if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        body.contains("统一身份认证", ignoreCase = true)) {
      throw resolveLocalBusinessAuthenticationFailure("card_error")
    }
  }

  private suspend fun parseBalanceResponse(response: HttpResponse): Result<CardBalanceData> {    val body = response.bodyAsText()
    if (isCardSessionExpired(response, body)) {
      return Result.failure(resolveLocalBusinessAuthenticationFailure("card_error"))
    }
    if (response.status != HttpStatusCode.OK) {
      return Result.failure(
          localBusinessApiException(
              "card_error",
              userFacingMessageForCode("card_error", response.status),
              response.status,
          )
      )
    }

    val payload =
        runCatching { json.decodeFromString<CardBalanceResponse>(body) }.getOrElse {
          return Result.failure(
              localBusinessApiException(
                  "card_error",
                  userFacingMessageForCode(
                      "card_error",
                      HttpStatusCode.InternalServerError,
                  ),
                  HttpStatusCode.InternalServerError,
              )
          )
        }

    if (!payload.success || payload.data == null) {
      return Result.failure(
          ApiCallException(
              message = "一卡通余额查询失败，请稍后重试",
              status = HttpStatusCode.BadGateway,
              code = "card_error",
          )
      )
    }

    return Result.success(payload.data)
  }

  private fun isCardSessionExpired(response: HttpResponse, body: String): Boolean {
    if (response.status == HttpStatusCode.Unauthorized) return true
    if (localIsSsoUrl(response.call.request.url.toString())) return true
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        body.contains("input name=\"execution\"") ||
        body.contains("统一身份认证", ignoreCase = true)
  }
}

/** JsonElement 安全转为 JsonObject，非对象（含 null）返回 null。 */
private fun JsonElement?.safeObject(): JsonObject? = this as? JsonObject
