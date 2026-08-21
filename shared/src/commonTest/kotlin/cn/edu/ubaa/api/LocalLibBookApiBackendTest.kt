package cn.edu.ubaa.api

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.core.DefaultApiFactory
import cn.edu.ubaa.api.local.LocalAuthSession
import cn.edu.ubaa.api.local.LocalAuthSessionStore
import cn.edu.ubaa.api.local.LocalCookieStore
import cn.edu.ubaa.api.local.LocalLibBookApiBackend
import cn.edu.ubaa.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.model.dto.UserData
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalLibBookApiBackendTest {
  @BeforeTest
  fun setup() {
    runTest { localConnectionTestMutex.lock() }
    ConnectionModeStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
    LocalAuthSessionStore.save(
        LocalAuthSession(
            username = "22373333",
            user = UserData(name = "Test User", schoolid = "22373333"),
            authenticatedAt = "2026-05-08T08:00:00Z",
            lastActivity = "2026-05-08T08:30:00Z",
        )
    )
    LocalUpstreamClientProvider.reset()
  }

  @AfterTest
  fun tearDown() {
    LocalUpstreamClientProvider.reset()
    LocalAuthSessionStore.clearAllScopes()
    LocalCookieStore.clearAllScopes()
    ConnectionRuntime.clearSelectedMode()
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `libbook backend exchanges cas token and calls v4 APIs`() = runTest {
    var casLoginCalls = 0
    var loginUserCalls = 0
    var reserveBody = ""
    var cancelBody = ""
    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/login" -> {
          casLoginCalls++
          respond(
              content = ByteReadChannel.Empty,
              status = HttpStatusCode.Found,
              headers =
                  headersOf(
                      HttpHeaders.Location,
                      "https://booking.lib.buaa.edu.cn/v4/login/cas?cas=cas-1",
                  ),
          )
        }
        "/v4/login/user" -> {
          loginUserCalls++
          assertContains(request.bodyText(), "\"cas\":\"cas-1\"")
          respondJson(
              """
              {
                "code":0,
                "data":{
                  "member":{
                    "token":"jwt-1",
                    "name":"测试用户"
                  }
                }
              }
              """
                  .trimIndent()
          )
        }
        "/v4/space/pcTopFor" -> {
          assertEquals("bearerjwt-1", request.headers[HttpHeaders.Authorization])
          respondJson(
              """
              {
                "code":1,
                "data":{
                  "list":[
                    {
                      "id":"9",
                      "name":"学院路校区图书馆",
                      "free_num":12,
                      "total_num":100,
                      "children":[
                        {"id":"10","name":"一层","free_num":5,"total_num":30}
                      ]
                    }
                  ]
                }
              }
              """
                  .trimIndent()
          )
        }
        "/v4/space/pick" ->
            respondJson(
                """
                {
                  "code":1,
                  "data":{
                    "area":[
                      {"id":"8","name":"一层西阅学空间","area":"学院路","free_num":2,"total_num":10}
                    ]
                  }
                }
                """
                    .trimIndent()
            )
        "/v4/Space/map" ->
            respondJson(
                """
                {
                  "code":1,
                  "data":{
                    "area":{"id":"8","name":"一层西阅学空间"},
                    "date":{
                      "list":[
                        {
                          "day":"2026-05-08",
                          "times":[{"id":"seg-1","start":"08:00","end":"23:00"}]
                        }
                      ]
                    }
                  }
                }
                """
                    .trimIndent()
            )
        "/v4/Space/seat" ->
            respondJson(
                """
                {
                  "code":1,
                  "data":{
                    "list":[
                      {"id":"101","name":"座位101","no":"101","status":"1","status_name":"空闲"},
                      {"id":"102","name":"座位102","no":"102","status":"2","status_name":"已占用"}
                    ]
                  }
                }
                """
                    .trimIndent()
            )
        "/v4/space/confirm" -> {
          reserveBody = request.bodyText()
          respondJson(
              """{"code":1,"message":"操作成功","data":{"bookInfo":{"id":"b1","nameMerge":"一层 / 101","no":"101"}}}"""
          )
        }
        "/v4/member/seat" ->
            respondJson(
                """
                {
                  "code":1,
                  "data":{
                    "data":[{"id":"b1","nameMerge":"一层 / 101","no":"101","beginTime":"08:00","endTime":"23:00","status_name":"已预约"}],
                    "total":1
                  }
                }
                """
                    .trimIndent()
            )
        "/v4/space/cancel" -> {
          cancelBody = request.bodyText()
          respondJson("""{"code":1,"message":"取消成功"}""")
        }
        else -> error("Unexpected request: ${request.url}")
      }
    }
    installMockClient(engine)

    val backend = LocalLibBookApiBackend()
    val libraries = backend.getLibraries("2026-05-08").getOrThrow()
    val areas = backend.getAreas("9", "10", "2026-05-08").getOrThrow()
    val detail = backend.getAreaDetail("8").getOrThrow()
    val seats = backend.getSeats("8", "2026-05-08", "08:00", "23:00").getOrThrow()
    val reserve =
        backend
            .reserve(
                LibBookReserveRequest(
                    areaId = "8",
                    seatId = "101",
                    day = "2026-05-08",
                    segment = "seg-1",
                )
            )
            .getOrThrow()
    val bookings = backend.getBookings().getOrThrow()
    val cancel = backend.cancelBooking("b1").getOrThrow()

    assertEquals(1, casLoginCalls)
    assertEquals(1, loginUserCalls)
    assertEquals("学院路校区图书馆", libraries.single().name)
    assertEquals("一层", libraries.single().storeys.single().name)
    assertEquals("一层西阅学空间", areas.single().name)
    assertEquals("seg-1", detail.timeSlots.single().id)
    assertEquals(listOf(true, false), seats.map { it.isAvailable })
    assertEquals("操作成功", reserve.message)
    assertContains(
        reserveBody,
        "lGWxL9YCYE0sXIQzPsUCs3jfaFPunT/NyR93uF2nVP1OQPYYihpMRBvm7jxYdUZNTMCyIRtdY8d3DgCNz8G3lmeWmPjvy6jV2KeuJXR8nrOmk26JK+ATZB1VXBNOFebA",
    )
    assertEquals(1, bookings.bookings.size)
    assertTrue(cancel.success)
    assertContains(cancelBody, "\"id\":\"b1\"")
  }

  @Test
  fun `libbook backend extracts cas token from h5 fragment redirect`() = runTest {
    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/login" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.Found,
                headers =
                    headersOf(
                        HttpHeaders.Location,
                        "https://booking.lib.buaa.edu.cn/v4/login/cas?ticket=ST-1",
                    ),
            )
        "/v4/login/cas" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.Found,
                headers =
                    headersOf(
                        HttpHeaders.Location,
                        "https://booking.lib.buaa.edu.cn/h5/index.html#/cas/?cas=cas-fragment",
                    ),
            )
        "/v4/login/user" -> {
          assertContains(request.bodyText(), "\"cas\":\"cas-fragment\"")
          respondJson(
              """
              {
                "code":0,
                "data":{
                  "member":{"token":"jwt-fragment","name":"测试用户"}
                }
              }
              """
                  .trimIndent()
          )
        }
        "/v4/space/pcTopFor" -> {
          assertEquals("bearerjwt-fragment", request.headers[HttpHeaders.Authorization])
          respondJson(
              """
              {
                "code":1,
                "data":{
                  "list":[
                    {"id":"9","name":"学院路校区图书馆","free_num":12,"total_num":100}
                  ]
                }
              }
              """
                  .trimIndent()
          )
        }
        else -> error("Unexpected request: ${request.url}")
      }
    }
    installMockClient(engine)

    val libraries = LocalLibBookApiBackend().getLibraries("2026-05-08").getOrThrow()

    assertEquals("学院路校区图书馆", libraries.single().name)
  }

  @Test
  fun `cancel ended booking maps to not found without breaking later requests`() = runTest {
    var loginUserCalls = 0
    var cancelCalls = 0
    var librariesCalls = 0
    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/login" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.Found,
                headers =
                    headersOf(
                        HttpHeaders.Location,
                        "https://booking.lib.buaa.edu.cn/v4/login/cas?cas=cas-ended",
                    ),
            )
        "/v4/login/user" -> {
          loginUserCalls++
          respondJson(
              """
              {
                "code":0,
                "data":{"member":{"token":"jwt-ended","name":"测试用户"}}
              }
              """
                  .trimIndent()
          )
        }
        "/v4/space/cancel" -> {
          cancelCalls++
          respondJson("""{"code":2,"message":"预约已结束，不能取消"}""")
        }
        "/v4/space/pcTopFor" -> {
          librariesCalls++
          assertEquals("bearerjwt-ended", request.headers[HttpHeaders.Authorization])
          respondJson(
              """
              {
                "code":1,
                "data":{"list":[{"id":"9","name":"学院路校区图书馆"}]}
              }
              """
                  .trimIndent()
          )
        }
        else -> error("Unexpected request: ${request.url}")
      }
    }
    installMockClient(engine)
    val backend = LocalLibBookApiBackend()

    val error = backend.cancelBooking("b-ended").exceptionOrNull()
    val libraries = backend.getLibraries("2026-05-08").getOrThrow()

    assertTrue(error is ApiCallException)
    assertEquals("libbook_not_found", error.code)
    assertEquals("学院路校区图书馆", libraries.single().name)
    assertEquals(1, loginUserCalls)
    assertEquals(1, cancelCalls)
    assertEquals(1, librariesCalls)
  }

  private fun installMockClient(engine: MockEngine) {
    val factory = { followRedirects: Boolean ->
      HttpClient(engine) {
        this.followRedirects = followRedirects
        install(ContentNegotiation) { json() }
        install(HttpCookies) { storage = LocalCookieStore.storage(ConnectionMode.DIRECT) }
      }
    }
    LocalUpstreamClientProvider.clientFactory = factory
    LocalUpstreamClientProvider.libBookClientFactory = factory
  }

  private fun io.ktor.client.request.HttpRequestData.bodyText(): String =
      when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> error("Unsupported request body: ${content::class.simpleName}")
      }
}

private fun MockRequestHandleScope.respondJson(body: String) =
    respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
