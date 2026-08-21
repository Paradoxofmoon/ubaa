package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.auth.userFacingMessageForCode
import cn.edu.ubaa.api.feature.ZfwApiBackend
import cn.edu.ubaa.api.feature.ZfwLoginResult
import cn.edu.ubaa.api.feature.ZfwPayPageData
import cn.edu.ubaa.api.feature.ZfwPayResult
import cn.edu.ubaa.api.network.DebugFileSink
import cn.edu.ubaa.api.network.platformLog
import cn.edu.ubaa.api.plantform.PlatformRsaPkcs1Encrypt
import cn.edu.ubaa.model.dto.TrafficData
import cn.edu.ubaa.model.dto.ZfwValidateResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json

internal class LocalZfwApiBackend : ZfwApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  private var sessionClient: HttpClient? = null
  private var sessionCookieStorage: AcceptAllCookiesStorage? = null
  private var lastLoginPage: LoginPageData? = null

  /** 创建全新的、仅内存 Cookie 的会话。每次刷新验证码时调用，以获得新的 PHP session 和验证码。 */
  private fun orCreateClient(): HttpClient {
    sessionClient?.close()
    val storage = AcceptAllCookiesStorage()
    sessionCookieStorage = storage
    val client = LocalUpstreamClientProvider.newClient(cookieStorage = storage)
    sessionClient = client
    return client
  }

  override suspend fun fetchCaptcha(): Pair<ByteArray, String> {
    val client = orCreateClient()
    val loginPage = fetchLoginPage(client)
    lastLoginPage = loginPage
    platformLog("ZFW", "fetchCaptcha: captchaSrc=${loginPage.captchaSrc}")
    val captchaUrl = resolveCaptchaUrl(loginPage.captchaSrc)
    val response =
        client.get(captchaUrl) {
          header(HttpHeaders.Accept, "image/*,*/*")
        }
    platformLog("ZFW", "fetchCaptcha: status=${response.status}")
    if (response.status != HttpStatusCode.OK) {
      throw localBusinessApiException(
          "zfw_captcha_error",
          "验证码获取失败",
          response.status,
      )
    }
    val bytes = response.body<ByteArray>()
    platformLog("ZFW", "fetchCaptcha: got ${bytes.size} bytes")
    // 不关闭 client，login() 将复用该会话
    return bytes to ""
  }

  override suspend fun login(
      username: String,
      password: String,
      captcha: String,
      smsCode: String?,
  ): Result<ZfwLoginResult> {
    if (username.isBlank() || password.isBlank()) {
      return Result.failure(ApiCallException("请输入账号和密码"))
    }

    return try {
      val client = sessionClient ?: return Result.failure(ApiCallException("请先获取验证码"))
      val loginPage =
          lastLoginPage
              ?: return Result.failure(ApiCallException("请先获取验证码"))
      val encryptedPassword = encryptPassword(password, loginPage.rsaPublicKeyPem)
      platformLog("ZFW", "login: username=$username, captchaLen=${captcha.length}, csrfParam=${loginPage.csrfParam}, rsaKeyFromPage=${loginPage.rsaPublicKeyPem.isNotBlank()}")
      val validateResponse =
          validateUser(
              client = client,
              loginPage = loginPage,
              username = username,
              password = encryptedPassword,
              captcha = captcha,
              smsCode = smsCode,
          )
      platformLog("ZFW", "validateUser: success=${validateResponse.success}, inputSms=${validateResponse.inputSms}, msg=${validateResponse.message}")

      if (!validateResponse.success) {
        return Result.failure(
            ApiCallException(validateResponse.message ?: "登录验证失败，请检查账号密码或验证码")
        )
      }

      if (validateResponse.inputSms) {
        return Result.success(
            ZfwLoginResult.NeedSms(
                message = validateResponse.message ?: "需要短信验证",
                remainSeconds = validateResponse.remain,
            )
        )
      }

      finalSubmit(
          client = client,
          loginPage = loginPage,
          username = username,
          password = encryptedPassword,
          captcha = captcha,
          smsCode = smsCode,
      )

      // 登录成功，提取会话 Cookie 供 WebView 注入使用
      val cookies = sessionCookieStorage?.get(Url(ZFW_BASE_URL)).orEmpty()
      platformLog("ZFW", "login success, cookies=${cookies.joinToString { it.name }}")
      // 调试：用会话 Cookie 抓取充值首页 HTML 以便分析 API
      try {
        val dashResponse =
            client.get(localUpstreamUrl(ZFW_BASE_URL)) {
              header(
                  HttpHeaders.Accept,
                  "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
              )
            }
        val dashHtml = dashResponse.bodyAsText()
        DebugFileSink.write("zfw_dashboard.html", dashHtml)
      } catch (e: Exception) {
        DebugFileSink.write("zfw_dashboard_error.txt", e.message ?: "unknown")
      }
      Result.success(ZfwLoginResult.Success(cookies))
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("校园网充值登录失败，请稍后重试"))
    }
  }

  override suspend fun getTraffic(): Result<TrafficData> {
    val client = sessionClient
    if (client == null) {
      return Result.failure(ApiCallException("请先登录校园网自助服务门户"))
    }

    return try {
      val response =
          client.get(localUpstreamUrl(ZFW_BASE_URL)) {
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
          }
      if (response.status != HttpStatusCode.OK) {
        return Result.failure(
            localBusinessApiException(
                "zfw_traffic_error",
                "流量查询失败，请稍后重试",
                response.status,
            )
        )
      }

      val html = response.bodyAsText()
      DebugFileSink.write("zfw_traffic.html", html)

      if (isLoginPage(html)) {
        // 会话已失效，需要重新登录
        return Result.failure(ApiCallException("登录已过期，请重新登录"))
      }

      Result.success(extractTrafficData(html))
    } catch (e: Exception) {
      if (e is ApiCallException) {
        Result.failure(e)
      } else {
        Result.failure(e.toUserFacingApiException("校园网流量查询失败，请稍后重试"))
      }
    }
  }

  /**
   * 从首页 HTML 的"产品信息"表格中提取流量数据。
   *
   * 表格列结构（data-col-seq）：
   * 1=产品名称, 2=计费策略, 3=已用流量, 4=已用时长, 6=免费流量剩余(不含套餐),
   * 7=计费流量剩余(不含套餐), 12=结算日期
   */
  private fun extractTrafficData(html: String): TrafficData {
    val tableMatch = PRODUCT_TABLE_REGEX.find(html)
    if (tableMatch == null) {
      throw ApiCallException("未找到流量数据表格，页面结构可能已变更")
    }
    val tableHtml = tableMatch.value

    // 提取"套餐详情"展开行的 tbody（data-key 行是实际数据行）
    val rowMatch = PRODUCT_ROW_REGEX.find(tableHtml)
    if (rowMatch == null) {
      throw ApiCallException("未找到流量数据行，页面结构可能已变更")
    }
    val rowHtml = rowMatch.value

    fun cell(seq: Int): String {
      val cellRegex =
          Regex(
              """<td[^>]*data-col-seq=["']${seq}["'][^>]*>([\s\S]*?)</td>""",
              RegexOption.IGNORE_CASE,
          )
      return cellRegex.find(rowHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    val usedRaw = cell(3) // 已用流量
    val usedSecondsRaw = cell(4) // 已用时长
    val freeRemainingRaw = cell(6) // 免费流量剩余
    val paidRemainingRaw = cell(7) // 计费流量剩余
    val settleDateRaw = cell(12) // 结算日期
    val billingPolicyRaw = cell(2) // 计费策略

    return TrafficData(
        usedTraffic = parseGb(usedRaw),
        usedSeconds = parseSeconds(usedSecondsRaw),
        freeTrafficRemaining = parseGb(freeRemainingRaw) ?: 0.0,
        paidTrafficRemaining = parseGb(paidRemainingRaw),
        settleDate = settleDateRaw.takeIf { it.isNotBlank() },
        billingPolicy = billingPolicyRaw.takeIf { it.isNotBlank() },
    )
  }

  /** 解析形如 "70.000G"、"0byte"、"1.5G" 的流量值，返回 GB。 */
  private fun parseGb(raw: String): Double? {
    if (raw.isBlank()) return null
    val match = GB_REGEX.find(raw) ?: return null
    return match.groupValues[1].toDoubleOrNull()
  }

  /** 解析形如 "0秒"、"1小时23分45秒" 的时长，返回秒。 */
  private fun parseSeconds(raw: String): Long? {
    if (raw.isBlank()) return null
    if (raw == "0秒") return 0L
    var total = 0L
    HOUR_REGEX.find(raw)?.let { m -> total += (m.groupValues[1].toLongOrNull() ?: 0L) * 3600 }
    MINUTE_REGEX.find(raw)?.let { m -> total += (m.groupValues[1].toLongOrNull() ?: 0L) * 60 }
    SECOND_REGEX.find(raw)?.let { m -> total += m.groupValues[1].toLongOrNull() ?: 0L }
    return total
  }

  override suspend fun fetchPayPage(): Result<ZfwPayPageData> {
    val client = sessionClient
        ?: return Result.failure(ApiCallException("请先登录校园网自助服务门户"))

    return try {
      val response =
          client.get(localUpstreamUrl("$ZFW_BASE_URL/pays")) {
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
          }
      val html = response.bodyAsText()
      DebugFileSink.write("zfw_pays.html", html)

      if (response.status != HttpStatusCode.OK) {
        return Result.failure(
            localBusinessApiException(
                "zfw_pay_error",
                "加载缴费页面失败",
                response.status,
            )
        )
      }
      if (isLoginPage(html)) {
        return Result.failure(ApiCallException("登录已过期，请重新登录"))
      }

      val csrfParam =
          CSRF_PARAM_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
              ?: DEFAULT_CSRF_PARAM
      val csrfToken =
          CSRF_TOKEN_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
      val cardNo =
          CARD_NO_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
      val productId =
          PRODUCT_ID_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()

      if (csrfToken.isBlank()) {
        return Result.failure(ApiCallException("缴费页面 CSRF 令牌解析失败"))
      }

      Result.success(
          ZfwPayPageData(
              cardNo = cardNo,
              productId = productId.ifBlank { "1" },
              csrfParam = csrfParam,
              csrfToken = csrfToken,
          )
      )
    } catch (e: Exception) {
      if (e is ApiCallException) Result.failure(e)
      else Result.failure(e.toUserFacingApiException("加载缴费页面失败，请稍后重试"))
    }
  }

  override suspend fun fetchPayCaptcha(): Result<ByteArray> {
    val client = sessionClient
        ?: return Result.failure(ApiCallException("请先登录校园网自助服务门户"))

    return try {
      val response =
          client.get(localUpstreamUrl("$ZFW_BASE_URL/pay/captcha")) {
            header(HttpHeaders.Accept, "image/*,*/*")
          }
      if (response.status != HttpStatusCode.OK) {
        return Result.failure(
            localBusinessApiException(
                "zfw_pay_captcha_error",
                "缴费验证码获取失败",
                response.status,
            )
        )
      }
      Result.success(response.body<ByteArray>())
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("缴费验证码获取失败，请稍后重试"))
    }
  }

  override suspend fun submitPay(
      amount: String,
      captcha: String,
      payPageData: ZfwPayPageData,
  ): Result<ZfwPayResult> {
    val client = sessionClient
        ?: return Result.failure(ApiCallException("请先登录校园网自助服务门户"))

    return try {
      val response =
          client.submitForm(
              url = localUpstreamUrl("$ZFW_BASE_URL/pay/card"),
              formParameters =
                  Parameters.build {
                    append("OneCardForm[cardNo]", payPageData.cardNo)
                    append("OneCardForm[productId]", payPageData.productId)
                    append("OneCardForm[amount]", amount)
                    append("OneCardForm[verifyCode]", captcha)
                    append(payPageData.csrfParam, payPageData.csrfToken)
                  },
          ) {
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.Referrer, localUpstreamUrl("$ZFW_BASE_URL/pays"))
          }

      val html = response.bodyAsText()
      DebugFileSink.write("zfw_pay_result.html", html)

      // 提取支付收银台地址（cashier.cc-pay.cn）
      val cashierUrl = extractCashierUrl(html)
      if (cashierUrl != null) {
        // 用带 session cookie 的 client 拉取二维码图片（/pay/qrcode 需要登录态）
        val qrcodeBytes = fetchQrcodeImage(client, cashierUrl)
        Result.success(ZfwPayResult.Success(cashierUrl = cashierUrl, qrcodeBytes = qrcodeBytes))
      } else {
        // 没有支付地址，可能是验证码错误或账号异常，尝试提取错误信息
        val error = extractPayError(html)
        Result.success(ZfwPayResult.Failure(error ?: "充值失败，请检查验证码或金额"))
      }
    } catch (e: Exception) {
      if (e is ApiCallException) Result.failure(e)
      else Result.failure(e.toUserFacingApiException("充值提交失败，请稍后重试"))
    }
  }

  /** 用登录会话拉取支付二维码图片字节。失败返回 null，不阻断充值流程。 */
  private suspend fun fetchQrcodeImage(client: HttpClient, cashierUrl: String): ByteArray? {
    return try {
      val qrcodeUrl = "$ZFW_BASE_URL/pay/qrcode?url=${encodeUrlParam(cashierUrl)}"
      val response =
          client.get(localUpstreamUrl(qrcodeUrl)) {
            header(HttpHeaders.Accept, "image/png,image/*,*/*")
          }
      if (response.status == HttpStatusCode.OK) {
        response.body<ByteArray>()
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  /** 从充值结果 HTML 中提取 cashier.cc-pay.cn 收银台地址。 */
  private fun extractCashierUrl(html: String): String? {
    val patterns =
        listOf(
            Regex("""cashier\.cc-pay\.cn[^"'\\\s<>]*""", RegexOption.IGNORE_CASE),
            Regex("""(?:https%3A%2F%2F|https://)cashier\.cc-pay\.cn[^"'\\\s<>]*"""),
        )
    for (p in patterns) {
      val raw = p.find(html)?.value ?: continue
      val decoded =
          if (raw.contains("%3A", ignoreCase = true) || raw.contains("%2F", ignoreCase = true)) {
            raw.replace("%3A", ":").replace("%2F", "/")
          } else {
            raw
          }
      return if (decoded.startsWith("https://") || decoded.startsWith("http://")) {
        decoded
      } else {
        "https://$decoded"
      }
    }
    return null
  }

  /** URL 参数 RFC3986 编码。 */
  private fun encodeUrlParam(value: String): String {
    val sb = StringBuilder()
    for (ch in value) {
      when {
        ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~' -> sb.append(ch)
        else -> {
          val bytes = ch.toString().encodeToByteArray()
          for (b in bytes) {
            sb.append('%')
            sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX_DIGITS[b.toInt() and 0x0F])
          }
        }
      }
    }
    return sb.toString()
  }

  /** 从充值结果 HTML 中提取错误信息（验证码错误、账号异常等）。 */
  private fun extractPayError(html: String): String? {
    val candidates =
        listOf(
            Regex("""<div[^>]*class=["'][^"']*help-block-error[^"']*["'][^>]*>([\s\S]*?)</div>"""),
            Regex("""<div[^>]*class=["'][^"']*alert[^"']*["'][^>]*>([\s\S]*?)</div>"""),
        )
    return candidates
        .asSequence()
        .mapNotNull { it.find(html)?.groupValues?.getOrNull(1)?.stripHtml()?.trim() }
        .firstOrNull { it.isNotBlank() }
  }

  private suspend fun fetchLoginPage(client: HttpClient): LoginPageData {
    val response =
        client.get(localUpstreamUrl(ZFW_BASE_URL)) {
          header(
              HttpHeaders.Accept,
              "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          )
        }
    if (response.status != HttpStatusCode.OK) {
      throw localBusinessApiException(
          "zfw_login_error",
          "加载登录页面失败",
          response.status,
      )
    }
    val html = response.bodyAsText()
    val csrfToken =
        CSRF_TOKEN_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val csrfParam =
        CSRF_PARAM_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val captchaSrc =
        CAPTCHA_SRC_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    val rsaPublicKeyPem =
        RSA_PUBLIC_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    if (csrfToken.isBlank()) {
      throw ApiCallException("登录页面安全令牌解析失败")
    }

    return LoginPageData(
        csrfToken = csrfToken,
        csrfParam = csrfParam.ifBlank { DEFAULT_CSRF_PARAM },
        captchaSrc = captchaSrc,
        rsaPublicKeyPem = rsaPublicKeyPem,
    )
  }

  private suspend fun validateUser(
      client: HttpClient,
      loginPage: LoginPageData,
      username: String,
      password: String,
      captcha: String,
      smsCode: String?,
  ): ZfwValidateResponse {
    val response =
        client.submitForm(
            url = localUpstreamUrl("$ZFW_BASE_URL/site/validate-user"),
            formParameters =
                buildLoginParameters(
                    loginPage = loginPage,
                    username = username,
                    password = password,
                    captcha = captcha,
                    smsCode = smsCode,
                ),
        ) {
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header("X-Requested-With", "XMLHttpRequest")
          header(HttpHeaders.Referrer, localUpstreamUrl(ZFW_BASE_URL))
        }

    val body = response.bodyAsText()
    platformLog("ZFW", "validateUser: status=${response.status}, body=${body.take(200)}")
    if (response.status != HttpStatusCode.OK) {
      throw localBusinessApiException(
          "zfw_validate_error",
          "登录验证请求失败",
          response.status,
      )
    }

    return runCatching { json.decodeFromString<ZfwValidateResponse>(body) }
        .getOrElse {
          throw ApiCallException("登录验证响应解析失败")
        }
  }

  private suspend fun finalSubmit(
      client: HttpClient,
      loginPage: LoginPageData,
      username: String,
      password: String,
      captcha: String,
      smsCode: String?,
  ) {
    val response =
        client.submitForm(
            url = localUpstreamUrl(ZFW_BASE_URL),
            formParameters =
                buildLoginParameters(
                    loginPage = loginPage,
                    username = username,
                    password = password,
                    captcha = captcha,
                    smsCode = smsCode,
                ),
        ) {
          header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
          header(HttpHeaders.Referrer, localUpstreamUrl(ZFW_BASE_URL))
        }

    val body = response.bodyAsText()
    platformLog("ZFW", "finalSubmit: status=${response.status}, isLoginPage=${isLoginPage(body)}, bodyLen=${body.length}")
    // 302/301 重定向 = 登录成功，服务器跳转到自助服务首页
    if (response.status == HttpStatusCode.Found || response.status == HttpStatusCode.MovedPermanently || response.status == HttpStatusCode.SeeOther) {
      platformLog("ZFW", "finalSubmit: login success via redirect")
      return
    }
    if (response.status != HttpStatusCode.OK) {
      throw localBusinessApiException(
          "zfw_login_error",
          "登录提交失败",
          response.status,
      )
    }

    if (isLoginPage(body)) {
      val error = extractLoginError(body)
      platformLog("ZFW", "finalSubmit failed: $error")
      throw ApiCallException(error ?: "登录失败，请检查账号密码或验证码")
    }
  }

  private fun buildLoginParameters(
      loginPage: LoginPageData,
      username: String,
      password: String,
      captcha: String,
      smsCode: String?,
  ): Parameters =
      Parameters.build {
        append("LoginForm[username]", username)
        append("LoginForm[password]", password)
        append("LoginForm[verifyCode]", captcha)
        append(loginPage.csrfParam, loginPage.csrfToken)
        // 兼容部分页面使用固定 _csrf 参数名的情况
        if (loginPage.csrfParam != DEFAULT_CSRF_PARAM) {
          append(DEFAULT_CSRF_PARAM, loginPage.csrfToken)
        }
        smsCode?.takeIf { it.isNotBlank() }?.let { append("LoginForm[smsCode]", it) }
      }

  private fun resolveCaptchaUrl(src: String): String {
    if (src.isBlank()) return localUpstreamUrl("$ZFW_BASE_URL/site/captcha")
    if (src.startsWith("http://") || src.startsWith("https://")) {
      return localUpstreamUrl(src)
    }
    if (src.startsWith("/")) {
      return localUpstreamUrl("$ZFW_BASE_URL$src")
    }
    return localUpstreamUrl("$ZFW_BASE_URL/$src")
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun encryptPassword(password: String, rsaPublicKeyPem: String): String {
    val derBytes = rsaPublicKeyDer(rsaPublicKeyPem)
    val encrypted = PlatformRsaPkcs1Encrypt.encrypt(password.encodeToByteArray(), derBytes)
    return Base64.encode(encrypted)
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun rsaPublicKeyDer(rsaPublicKeyPem: String): ByteArray {
    val pem = rsaPublicKeyPem.ifBlank { RSA_PUBLIC_KEY_PEM }
    val body =
        pem.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("-----BEGIN") || it.startsWith("-----END") }
            .joinToString("")
    return Base64.decode(body)
  }

  private fun isLoginPage(html: String): Boolean {
    val trimmed = html.trimStart()
    if (!trimmed.startsWith("<!DOCTYPE", ignoreCase = true) &&
        !trimmed.startsWith("<html", ignoreCase = true)
    ) {
      return false
    }
    // 只认登录页独有的特征：登录表单和登录验证码。
    // 注意：csrf-param 登录页和已登录页都有，不能作为判断依据，否则已登录的 /pays 页会被误判为登录页。
    return html.contains("id=\"login-form\"") ||
        html.contains("id=\"loginform-verifycode-image\"")
  }

  private fun extractLoginError(html: String): String? {
    val candidates =
        listOf(
            Regex("""<div[^>]*class=["'][^"']*alert[^"']*["'][^>]*>([\s\S]*?)</div>"""),
            Regex("""<div[^>]*class=["'][^"']*help-block[^"']*["'][^>]*>([\s\S]*?)</div>"""),
            Regex("""<p[^>]*class=["'][^"']*error[^"']*["'][^>]*>([\s\S]*?)</p>"""),
        )
    return candidates
        .asSequence()
        .mapNotNull { it.find(html)?.groupValues?.getOrNull(1)?.stripHtml()?.trim() }
        .firstOrNull { it.isNotBlank() }
  }

  private fun String.stripHtml(): String =
      replace(Regex("<[^>]+>"), " ")
          .replace(Regex("\\s+"), " ")
          .trim()

  private data class LoginPageData(
      val csrfToken: String,
      val csrfParam: String,
      val captchaSrc: String,
      val rsaPublicKeyPem: String = "",
  )

  companion object {
    private const val ZFW_BASE_URL = "https://zfw.buaa.edu.cn"
    private const val DEFAULT_CSRF_PARAM = "_csrf"

    private val CSRF_TOKEN_REGEX =
        Regex("""<meta[^>]*name=["']csrf-token["'][^>]*content=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val CSRF_PARAM_REGEX =
        Regex("""<meta[^>]*name=["']csrf-param["'][^>]*content=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val CAPTCHA_SRC_REGEX =
        Regex("""<img[^>]*id=["']loginform-verifycode-image["'][^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val RSA_PUBLIC_KEY_REGEX =
        Regex("""<input[^>]*id=["']public["'][^>]*value=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    // 流量解析相关正则
    /**
     * 匹配"产品信息"表格——即包含"免费流量剩余"表头的那个 table。
     * 首页有多个 kv-grid-table（在线信息表、产品信息表），必须精确定位产品信息表。
     */
    private val PRODUCT_TABLE_REGEX =
        Regex(
            """<table[^>]*class=["'][^"']*kv-grid-table[^"']*["'][^>]*>[\s\S]*?免费流量剩余[\s\S]*?</table>""",
            RegexOption.IGNORE_CASE,
        )
    /** 匹配表格中带 data-key 的实际数据行。 */
    private val PRODUCT_ROW_REGEX =
        Regex("""<tr[^>]*data-key=["'][^"']+["'][^>]*>[\s\S]*?</tr>""", RegexOption.IGNORE_CASE)
    /** 匹配流量数值，"70.000G"、"0byte" 等。 */
    private val GB_REGEX = Regex("""(\d+(?:\.\d+)?)\s*[Gg]""", RegexOption.IGNORE_CASE)
    private val HOUR_REGEX = Regex("""(\d+)\s*小时""")
    private val MINUTE_REGEX = Regex("""(\d+)\s*分""")
    private val SECOND_REGEX = Regex("""(\d+)\s*秒""")

    // 充值相关正则
    /** 匹配一卡通账号（value 属性在 disabled input 中）。 */
    private val CARD_NO_REGEX =
        Regex("""<input[^>]*name=["']OneCardForm\[cardNo\]["'][^>]*value=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    /** 匹配产品 ID（select 里的 option value）。 */
    private val PRODUCT_ID_REGEX =
        Regex("""<select[^>]*name=["']OneCardForm\[productId\]["'][\s\S]*?<option[^>]*value=["']([^"']+)["'][^>]*>[\s\S]*?</select>""", RegexOption.IGNORE_CASE)

    private const val HEX_DIGITS = "0123456789ABCDEF"

    private const val RSA_PUBLIC_KEY_PEM =
        """-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDD/uOBjC6KB0mG6Tdo0HFkZQsJ
c06YHlL/DIdwRiK+SFFWHTytP2UQOsktFOvJhbNwUhCGNJ1+mvJCgBhqu59k/9J0
CX1les4iSFUF4g1QlLPn2WD7IOuQd4hTdn5uVBcri0QgS4ji5z6zmYAN7wsgogua
hFUJbpRfCsgV02MnIQIDAQAB
-----END PUBLIC KEY-----"""
  }
}
