package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.ConnectionMode
import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.feature.BusApi
import cn.edu.ubaa.api.feature.BusApiBackend
import cn.edu.ubaa.api.network.platformLog
import cn.edu.ubaa.model.dto.BusBuyResultDto
import cn.edu.ubaa.model.dto.BusIndexPageDto
import cn.edu.ubaa.model.dto.BusSessionUserDto
import cn.edu.ubaa.model.dto.BusShiftDto
import cn.edu.ubaa.model.dto.BusShiftsResponse
import cn.edu.ubaa.model.dto.BusTicketDetailDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 智慧校车直达直连后端（zhihuixiaoche.buaa.edu.cn）。
 *
 * 认证：复用 app 已建立的直连 SSO（sso.buaa.edu.cn）会话 → CAS service=校车 CASLogin → 校车服务端校验 ticket 后种下 `beihang2`
 * 会话 cookie。后续 /wechat 接口全靠该 cookie，无 header 鉴权， 因此本后端所有请求一律走 DIRECT cookie + 直连地址（站点公网可达，与 cgyy
 * 同理）。
 */
internal class LocalBusApiBackend : BusApiBackend {
  private val json = Json { ignoreUnknownKeys = true }
  private val loginMutex = Mutex()
  private var sessionReady = false
  private var indexCsrfToken: String? = null

  private val baseUrl = "https://zhihuixiaoche.buaa.edu.cn"
  private val referrerUrl = "$baseUrl/wechat/indexPage"

  private fun busUrl(path: String): String = "$baseUrl$path"

  private fun directClient(followRedirects: Boolean = true): HttpClient =
      LocalUpstreamClientProvider.newClient(
          cookieStorage = LocalCookieStore.storage(ConnectionMode.DIRECT),
          followRedirects = followRedirects,
      )

  // ---------- 公开接口 ----------

  override suspend fun getIndexPage(): Result<BusIndexPageDto> =
      execute("校车页面加载失败，请稍后重试") {
        val html = sessionGet("/wechat/indexPage").bodyAsText()
        val page = parseIndexPage(html)
        indexCsrfToken = page.csrfToken.takeIf { it.isNotBlank() }
        page
      }

  override suspend fun getSessionUser(): Result<BusSessionUserDto> =
      execute("用户信息加载失败，请稍后重试") {
        val body =
            sessionPost(
                    "/wechat/waitingOrder",
                    form = mapOf("status" to "1", "page" to "-1"),
                )
                .bodyAsText()
        val raw = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (raw == null) BusSessionUserDto()
        else {
          BusSessionUserDto(
              name = raw["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
              tempNumber = raw["tempNumber"]?.jsonPrimitive?.contentOrNull.orEmpty(),
              success = raw["success"]?.jsonPrimitive?.contentOrNull == "true",
          )
        }
      }

  override suspend fun searchShifts(
      origin: String,
      terminal: String,
      date: String,
  ): Result<BusShiftsResponse> =
      execute("车次查询失败，请稍后重试") {
        val csrf =
            indexCsrfToken
                ?: parseIndexPage(sessionGet("/wechat/indexPage").bodyAsText()).csrfToken.also {
                  indexCsrfToken = it
                }
        val response =
            sessionPost(
                "/wechat/ShiftsSearch",
                form =
                    mapOf(
                        "up_origin_name" to origin,
                        "up_terminal_name" to terminal,
                        "shifts_date" to date,
                        "act" to "search",
                    ),
                csrf = csrf,
            )
        val raw = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val shifts =
            raw["list"]
                ?.jsonArray
                ?.mapNotNull { element ->
                  val o = element.jsonObject
                  val openSeat =
                      o["open_seat_num"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                  val student = o["student_num"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                  val teacher = o["teacher_num"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                  BusShiftDto(
                      depart_time = o["depart_time"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      arrive_time = o["arrive_time"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      is_shuttle =
                          o["is_shuttle"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                      line_name = o["line_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      open_seat_num = openSeat,
                      shifts_date = o["shifts_date"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      shifts_number = o["shifts_number"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      student_num = student,
                      teacher_num = teacher,
                      up_origin_name = o["up_origin_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                      up_terminal_name =
                          o["up_terminal_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                  )
                }
                .orEmpty()
        val type = raw["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val currentTime = raw["currentTime"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val seatRetain = raw["seat_retain_num"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val ticketAllMinute =
            raw["ticket_all_minute"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 15
        val isParttime = raw["is_parttime"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        BusShiftsResponse(
            success = raw["success"]?.jsonPrimitive?.contentOrNull == "true",
            message = raw["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            list =
                shifts.map { shift ->
                  shift.copy(
                      type = type,
                      ticketNum =
                          computeTicketNum(
                              shift = shift,
                              type = type,
                              currentTime = currentTime,
                              seatRetain = seatRetain,
                              ticketAllMinute = ticketAllMinute,
                              isParttime = isParttime,
                          ),
                  )
                },
            type = type,
            currentTime = currentTime,
            seat_retain_num = seatRetain,
            ticket_all_minute = ticketAllMinute,
            is_parttime = isParttime,
        )
      }

  override suspend fun getTicketDetail(
      date: String,
      shiftsNumber: String,
  ): Result<BusTicketDetailDto> =
      execute("车票信息加载失败，请稍后重试") {
        val html =
            sessionGet(
                    "/wechat/ticketInfoPage",
                    params =
                        mapOf(
                            "shifts_date" to date,
                            "shifts_number" to shiftsNumber,
                        ),
                )
                .bodyAsText()
        if (html.contains("未到购票时间")) {
          throw ApiCallException("该车次未到购票时间，暂不可订", HttpStatusCode.BadRequest, "bus_not_open")
        }
        parseTicketDetail(html, date, shiftsNumber)
      }

  override suspend fun getCaptchaImage(): Result<ByteArray> =
      execute("验证码加载失败，请稍后重试") {
        val response =
            sessionGet(
                "/wechat/getCaptchaImage",
                params =
                    mapOf("_t" to kotlin.time.Clock.System.now().toEpochMilliseconds().toString()),
            )
        readBodyBytes(response)
      }

  override suspend fun buyTicket(
      date: String,
      shiftsNumber: String,
      checkStr: String,
      csrfToken: String,
  ): Result<BusBuyResultDto> =
      execute("订票失败，请稍后重试") {
        val response =
            sessionPost(
                "/wechat/buyTicketForWX",
                form =
                    mapOf(
                        "checkStr" to checkStr,
                        "shifts_date" to date,
                        "shifts_number" to shiftsNumber,
                    ),
                csrf = csrfToken,
            )
        val raw = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val result =
            BusBuyResultDto(
                status = raw["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                message = raw["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                url = raw["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                orderId = raw["orderId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                code = raw["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                price = raw["price"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        if (result.status != "1") {
          throw ApiCallException(
              result.message.ifBlank { "订票失败" },
              HttpStatusCode.BadRequest,
              "bus_buy_failed",
          )
        }
        result
      }

  // ---------- 会话 ----------

  private suspend fun ensureLogin(force: Boolean = false) {
    LocalAuthSessionStore.get()
        ?: throw ApiCallException("登录状态已失效，请重新登录", HttpStatusCode.Unauthorized, "unauthenticated")
    if (!force && sessionReady) return
    loginMutex.withLock {
      if (!force && sessionReady) return@withLock
      sessionReady = false
      val storage = LocalCookieStore.storage(ConnectionMode.DIRECT)
      try {
        // 0) 已有校车 beihang2 会话直接可用
        if (hasBeihang2(storage)) {
          sessionReady = true
          return@withLock
        }
        // 1) CAS ticket 跳转（SSO 登记的 service 是 http 形态；手动跟随规避 ktor https→http 不跟随）
        if (tryCasLogin()) {
          sessionReady = true
          return@withLock
        }
        // 2) 北航站点通用登录态 sso_buaa_token 直连（cgyy 同款：DIRECT client 自动携带 .buaa.edu.cn cookie）
        if (tryTokenDirectLogin(storage)) {
          sessionReady = true
          return@withLock
        }
        val names =
            storage.get(Url("https://zhihuixiaoche.buaa.edu.cn/")).map { it.name }.distinct()
        throw ApiCallException(
            "校车登录失败：未建立直连会话，请先在直连模式重新登录后再试（cookie=${names.joinToString(",")}）",
            HttpStatusCode.Unauthorized,
            "unauthenticated",
        )
      } finally {
        Unit
      }
    }
  }

  /** CAS ticket 跳转：手动跟随重定向（SSO 302 → http 校车 CASLogin → beihang2）。 */
  private suspend fun tryCasLogin(): Boolean {
    val client = directClient(followRedirects = false)
    try {
      val service = "http://zhihuixiaoche.buaa.edu.cn/wechat/CASLogin"
      var url = "https://sso.buaa.edu.cn/login?service=${urlEncode(service)}"
      var steps = 0
      while (steps++ < 8) {
        val response =
            client.get(url) {
              header(
                  HttpHeaders.Accept,
                  "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
              )
              header(HttpHeaders.UserAgent, BUS_USER_AGENT)
            }
        val location = response.headers[HttpHeaders.Location]
        if (location.isNullOrBlank()) {
          platformLog("BUS", "CAS final status=${response.status} url=${response.call.request.url}")
          return hasBeihang2(LocalCookieStore.storage(ConnectionMode.DIRECT))
        }
        url = resolveBusRedirect(response.call.request.url.toString(), location)
        platformLog("BUS", "CAS step=$steps status=${response.status} -> $url")
      }
      return false
    } catch (e: Exception) {
      platformLog("BUS", "CAS manual failed: ${e.message}")
      return false
    } finally {
      client.close()
    }
  }

  private fun urlEncode(value: String): String = buildString {
    value.forEach { c ->
      if (c.isLetterOrDigit() || c in "-_.~") {
        append(c)
      } else {
        append('%').append((c.code and 0xFF).toString(16).uppercase().padStart(2, '0'))
      }
    }
  }

  private fun resolveBusRedirect(current: String, location: String): String {
    if (location.startsWith("http://") || location.startsWith("https://")) return location
    if (location.startsWith("//")) {
      val proto = current.substringBefore("://")
      return "$proto:$location"
    }
    val base = current.substringBefore("?")
    val scheme = base.substringBefore("://")
    val host = base.substringAfter("://").substringBefore("/")
    val origin = "$scheme://$host"
    return if (location.startsWith("/")) "$origin$location" else "$origin/$location"
  }

  /** sso_buaa_token 直连：校车同属 *.buaa.edu.cn，DIRECT client 自动携带 token，直接访问首页验证。 */
  private suspend fun tryTokenDirectLogin(storage: PersistentLocalCookieStorage): Boolean {
    val client = directClient(followRedirects = true)
    try {
      val response =
          client.get(busUrl("/wechat/indexPage")) {
            header(
                HttpHeaders.Accept,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            header(HttpHeaders.Referrer, referrerUrl)
            header(HttpHeaders.UserAgent, BUS_USER_AGENT)
          }
      val body = response.bodyAsText()
      val authed = !body.contains("未获取到认证信息")
      platformLog("BUS", "tokenDirect status=${response.status} len=${body.length} authed=$authed")
      if (!authed) return false
      if (hasBeihang2(storage)) return true
      // 校车若认 token 但不种 beihang2，后续请求持续带 token 也能用
      return body.contains("csrf_token") || body.contains("shiftsDateList")
    } catch (e: Exception) {
      platformLog("BUS", "tokenDirect failed: ${e.message}")
      return false
    } finally {
      client.close()
    }
  }

  private suspend fun hasBeihang2(storage: PersistentLocalCookieStorage): Boolean =
      storage.get(Url(busUrl("/wechat/indexPage"))).any {
        it.name == "beihang2" && it.value.isNotBlank()
      }

  private suspend fun sessionGet(
      path: String,
      params: Map<String, String> = emptyMap(),
  ): HttpResponse = withSessionRetry {
    directClient().get(busUrl(path)) {
      params.forEach { (k, v) -> parameter(k, v) }
      header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      header(HttpHeaders.Referrer, referrerUrl)
      header(HttpHeaders.UserAgent, BUS_USER_AGENT)
    }
  }

  private suspend fun sessionPost(
      path: String,
      form: Map<String, String>,
      csrf: String? = null,
  ): HttpResponse = withSessionRetry {
    directClient().post(busUrl(path)) {
      header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
      header(HttpHeaders.Referrer, referrerUrl)
      header(HttpHeaders.UserAgent, BUS_USER_AGENT)
      header("X-Requested-With", "XMLHttpRequest")
      if (!csrf.isNullOrBlank()) {
        header("X-CSRF-TOKEN", csrf)
      }
      val parameters = Parameters.build { form.forEach { (k, v) -> append(k, v) } }
      setBody(FormDataContent(parameters))
    }
  }

  private suspend fun readBodyBytes(response: HttpResponse): ByteArray = response.body()

  private suspend fun withSessionRetry(block: suspend () -> HttpResponse): HttpResponse {
    ensureLogin()
    var response = block()
    if (isLoginRedirect(response)) {
      ensureLogin(force = true)
      response = block()
    }
    return response
  }

  private fun isLoginRedirect(response: HttpResponse): Boolean {
    if (response.status == HttpStatusCode.Unauthorized) return true
    val finalUrl = response.call.request.url.toString()
    return finalUrl.contains("sso.buaa.edu.cn", ignoreCase = true)
  }

  private suspend fun <T> execute(
      fallbackMessage: String,
      block: suspend () -> T,
  ): Result<T> =
      runCatching { block() }
          .fold(
              onSuccess = { Result.success(it) },
              onFailure = { Result.failure(it.toUserFacingApiException(fallbackMessage)) },
          )

  // ---------- 解析 ----------

  private fun parseIndexPage(html: String): BusIndexPageDto {
    val dates =
        Regex("""'shifts_date'\s*:\s*'([0-9-]+)'""")
            .findAll(html)
            .map { it.groupValues[1] }
            .toList()
    val csrf =
        Regex("""var\s+csrf_token\s*=\s*'([^']+)'""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    val nowTime =
        Regex("""var\s+nowTime\s*=\s*(\d+)""").find(html)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: 0L
    return BusIndexPageDto(shiftsDateList = dates, csrfToken = csrf, nowTime = nowTime)
  }

  private fun parseTicketDetail(
      html: String,
      date: String,
      shiftsNumber: String,
  ): BusTicketDetailDto {
    fun first(pattern: String): String =
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    val departTime =
        Regex(
                """<div class="left">日期</div>\s*<div class="right">\s*<span>[0-9-]+</span>\s*<span[^>]*>([0-9:]+)</span>\s*<span[^>]*>([^<]+)</span>"""
            )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    val weekday =
        Regex(
                """<div class="left">日期</div>\s*<div class="right">\s*<span>[0-9-]+</span>\s*<span[^>]*>[0-9:]+</span>\s*<span[^>]*>([^<]+)</span>"""
            )
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    val category = first("""<div class="left">类别</div>\s*<div class="right">([^<]+)</div>""")
    val remainingText = first("""<div class="left">余票</div>\s*<div class="right">(\d+)\s*张</div>""")
    val priceText = first("""<div class="left">票价</div>\s*<div class="right">(\d+)\s*元</div>""")
    val shiftStation =
        Regex("""<div id="shift_station">([\s\S]*?)</div>\s*</div>""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    val stations =
        Regex("""<div>([^<]+)</div>""")
            .findAll(shiftStation)
            .map { it.groupValues[1].trim() }
            .toList()
    val origin = stations.getOrNull(0).orEmpty()
    val terminal = stations.getOrNull(2).orEmpty()
    val csrf = first("""var\s+csrf_token\s*=\s*'([^']+)'""")
    return BusTicketDetailDto(
        shiftsDate = date,
        departTime = departTime,
        weekday = weekday,
        category = category,
        remainingTickets = remainingText.toIntOrNull() ?: -1,
        price = priceText,
        origin = origin,
        terminal = terminal,
        shiftsNumber = shiftsNumber,
        csrfToken = csrf,
    )
  }

  /** 前端同款余票公式。 */
  private fun computeTicketNum(
      shift: BusShiftDto,
      type: Int,
      currentTime: Long,
      seatRetain: Int,
      ticketAllMinute: Int,
      isParttime: Int,
  ): Int {
    if (shift.is_shuttle == 1) return 0
    var ticketNum = shift.open_seat_num - shift.student_num - shift.teacher_num
    if (type != 1 && isParttime != 1) {
      val departEpoch =
          runCatching {
                LocalDateTime.parse("${shift.shifts_date}T${shift.depart_time}")
                    .toInstant(TimeZone.of("Asia/Shanghai"))
                    .toEpochMilliseconds()
              }
              .getOrNull() ?: 0L
      if (departEpoch > 0 && currentTime < departEpoch - ticketAllMinute * 60_000L) {
        ticketNum -= seatRetain
      }
    }
    return if (ticketNum < 0) 0 else ticketNum
  }

  companion object {
    const val BASE_URL = "https://zhihuixiaoche.buaa.edu.cn"
    const val BUS_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0"
  }
}

/** 智慧校车直达直连 API（走 LocalBusApiBackend）。 */
fun busDirectApi(): BusApi = BusApi(LocalBusApiBackend())
