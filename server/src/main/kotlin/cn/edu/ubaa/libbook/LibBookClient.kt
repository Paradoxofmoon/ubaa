package cn.edu.ubaa.libbook

import cn.edu.ubaa.auth.GlobalSessionManager
import cn.edu.ubaa.auth.SessionManager
import cn.edu.ubaa.metrics.AppObservability
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.utils.VpnCipher
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class LibBookApiEnvelope(val data: JsonElement?, val raw: JsonObject)

interface LibBookGateway {
  suspend fun getLibraries(day: String): JsonArray

  suspend fun getAreas(premisesId: String, storeyId: String?, day: String): JsonArray

  suspend fun getAreaInfo(areaId: String): JsonObject

  suspend fun getSeats(areaId: String, day: String, startTime: String, endTime: String): JsonArray

  suspend fun reserve(request: LibBookReserveRequest): JsonObject

  suspend fun getBookings(page: Int, limit: Int): JsonObject

  suspend fun cancelBooking(bookingId: String): JsonObject

  fun close()
}

class LibBookClient(
    private val username: String,
    private val sessionManager: SessionManager = GlobalSessionManager.instance,
) : LibBookGateway {
  private val json = Json { ignoreUnknownKeys = true }
  private val loginMutex = Mutex()
  @Volatile private var token: String? = null

  override suspend fun getLibraries(day: String): JsonArray =
      requestJson("list_libraries", "space/pcTopFor", buildJsonObject { put("day", day) })
          .dataList("list")

  override suspend fun getAreas(premisesId: String, storeyId: String?, day: String): JsonArray =
      requestJson(
              operation = "list_areas",
              path = "space/pick",
              body =
                  buildJsonObject {
                    put("premisesIds", premisesId)
                    putJsonArray("categoryIds") {}
                    putJsonArray("storeyIds") {
                      if (!storeyId.isNullOrBlank()) add(JsonPrimitive(storeyId))
                    }
                    putJsonArray("boutiqueIds") {}
                    put("date", day)
                  },
          )
          .dataList("area")

  override suspend fun getAreaInfo(areaId: String): JsonObject =
      requestJson("get_area_info", "Space/map", buildJsonObject { put("id", areaId) }).raw

  override suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): JsonArray =
      requestJson(
              operation = "list_seats",
              path = "Space/seat",
              body =
                  buildJsonObject {
                    put("id", areaId)
                    put("day", day)
                    putJsonArray("label_id") {}
                    put("start_time", startTime)
                    put("end_time", endTime)
                    put("begdate", "")
                    put("enddate", "")
                  },
          )
          .dataList("list")

  override suspend fun reserve(request: LibBookReserveRequest): JsonObject =
      requestJson(
              operation = "reserve",
              path = "space/confirm",
              body =
                  buildJsonObject { put("aesjson", LibBookCrypto.encryptReserveRequest(request)) },
          )
          .raw

  override suspend fun getBookings(page: Int, limit: Int): JsonObject =
      requestJson(
              operation = "list_bookings",
              path = "member/seat",
              body =
                  buildJsonObject {
                    put("type", "1")
                    put("page", page)
                    put("limit", limit)
                  },
          )
          .raw

  override suspend fun cancelBooking(bookingId: String): JsonObject =
      requestJson("cancel_booking", "space/cancel", buildJsonObject { put("id", bookingId) }).raw

  override fun close() {
    token = null
  }

  private suspend fun requestJson(
      operation: String,
      path: String,
      body: JsonObject,
      includeAuthorization: Boolean = true,
      allowRetry: Boolean = true,
  ): LibBookApiEnvelope {
    if (includeAuthorization) ensureLogin()
    val session = sessionManager.requireSession(username)
    val response =
        AppObservability.observeUpstreamRequest("libbook", operation) {
          session.client.post(normalizeUrl("$BASE_URL/v4/${path.removePrefix("/")}")) {
            applyLibBookHeaders()
            if (includeAuthorization) {
              header(HttpHeaders.Authorization, "bearer${requireNotNull(token)}")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
          }
        }
    val raw = parseJsonObject(response)
    if (isLoginExpired(raw)) {
      token = null
      if (!allowRetry) throw LibBookAuthenticationException("图书馆登录状态失效")
      ensureLogin(forceRefresh = true)
      return requestJson(operation, path, body, includeAuthorization, allowRetry = false)
    }
    val code = raw["code"]?.jsonPrimitive?.intOrNull
    if (code != null && code !in setOf(0, 1)) {
      throw LibBookException(
          raw.messageOrDefault("图书馆接口请求失败"),
          raw.messageToErrorCode(),
      )
    }
    return LibBookApiEnvelope(raw["data"], raw)
  }

  private suspend fun ensureLogin(forceRefresh: Boolean = false) {
    if (!forceRefresh && !token.isNullOrBlank()) return

    loginMutex.withLock {
      if (!forceRefresh && !token.isNullOrBlank()) return@withLock
      token = null
      val cas = fetchCasToken()
      val response =
          requestJson(
              operation = "login",
              path = "login/user",
              body = buildJsonObject { put("cas", cas) },
              includeAuthorization = false,
              allowRetry = false,
          )
      val member = response.raw["data"]?.jsonObjectOrNull()?.get("member")?.jsonObjectOrNull()
      token =
          member?.string("token")?.takeIf { it.isNotBlank() }
              ?: throw LibBookAuthenticationException("图书馆登录成功但未返回 token")
    }
  }

  private suspend fun fetchCasToken(): String {
    val baseClient = sessionManager.requireSession(username).client
    val noRedirectClient = baseClient.config { followRedirects = false }
    try {
      var currentUrl = normalizeUrl(CAS_LOGIN_URL)
      repeat(8) {
        val response =
            AppObservability.observeUpstreamRequest("libbook", "cas_login") {
              noRedirectClient.get(currentUrl) { applyLibBookHeaders() }
            }
        extractCasToken(response.call.request.url.toString())?.let {
          return it
        }
        extractCasToken(response.headers[HttpHeaders.Location])?.let {
          return it
        }
        val location =
            response.headers[HttpHeaders.Location]
                ?: throw LibBookAuthenticationException("图书馆 CAS 登录跳转缺少 Location")
        currentUrl = normalizeUrl(resolveUrl(response.call.request.url.toString(), location))
      }
      throw LibBookAuthenticationException("未能获取图书馆 CAS 参数")
    } finally {
      noRedirectClient.close()
    }
  }

  private fun io.ktor.client.request.HttpRequestBuilder.applyLibBookHeaders() {
    header(HttpHeaders.Accept, "application/json, text/plain, */*")
    header(HttpHeaders.UserAgent, USER_AGENT)
    header("X-Requested-With", "XMLHttpRequest")
    header(HttpHeaders.Referrer, normalizeUrl(BASE_URL))
    header(HttpHeaders.Origin, normalizeUrl(BASE_URL))
  }

  private suspend fun parseJsonObject(response: HttpResponse): JsonObject {
    if (response.status != HttpStatusCode.OK) {
      throw LibBookException("图书馆接口响应异常", response.status.toLibBookErrorCode())
    }
    val body = response.bodyAsText()
    return runCatching { json.parseToJsonElement(body).jsonObject }
        .getOrElse { throw LibBookException("图书馆接口返回了非 JSON 响应", "libbook_error") }
  }

  private fun isLoginExpired(raw: JsonObject): Boolean {
    val message = raw.messageOrDefault("")
    return message.contains("登录失效") || message.contains("请重新登录") || message.contains("未登录")
  }

  private fun extractCasToken(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching { Url(url).parameters["cas"] }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: Regex("""[?&#]cas=([^&]+)""").find(url)?.groupValues?.getOrNull(1)
  }

  private fun resolveUrl(baseUrl: String, location: String): String {
    if (location.startsWith("http://") || location.startsWith("https://")) return location
    val base = URI.create(baseUrl)
    return base.resolve(location).toString()
  }

  private fun normalizeUrl(url: String): String = VpnCipher.toVpnUrl(url)

  companion object {
    private const val BASE_URL = "https://booking.lib.buaa.edu.cn"
    private const val CAS_LOGIN_URL =
        "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fbooking.lib.buaa.edu.cn%2Fv4%2Flogin%2Fcas"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
  }
}

open class LibBookException(message: String, val code: String = "libbook_error") :
    RuntimeException(message)

class LibBookAuthenticationException(message: String) :
    LibBookException(message, "libbook_auth_failed")

private fun LibBookApiEnvelope.dataList(key: String): JsonArray =
    data?.jsonObjectOrNull()?.get(key)?.jsonArrayOrNull() ?: buildJsonArray {}

internal fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

internal fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull
        ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        ?: 0

internal fun JsonObject.messageOrDefault(default: String): String =
    string("message").ifBlank { string("msg").ifBlank { default } }

internal fun JsonObject.messageToErrorCode(): String {
  val message = messageOrDefault("")
  return when {
    message.contains("已取消") ||
        message.contains("用户取消") ||
        message.contains("已结束") ||
        message.contains("不能取消") ||
        message.contains("无法取消") ||
        message.contains("已完成") ||
        message.contains("不存在") ||
        message.contains("失效") -> "libbook_not_found"
    message.contains("已被") || message.contains("不可") || message.contains("无可预约") ->
        "libbook_seat_unavailable"
    else -> "libbook_error"
  }
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

private fun HttpStatusCode.toLibBookErrorCode(): String =
    when (this) {
      HttpStatusCode.RequestTimeout,
      HttpStatusCode.GatewayTimeout -> "libbook_timeout"
      HttpStatusCode.Unauthorized,
      HttpStatusCode.Forbidden -> "libbook_auth_failed"
      HttpStatusCode.NotFound -> "libbook_not_found"
      else -> "libbook_error"
    }
