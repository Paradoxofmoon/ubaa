package cn.edu.ubaa.ygdk

import cn.edu.ubaa.auth.InMemoryCookieStorageFactory
import cn.edu.ubaa.auth.InMemorySessionStore
import cn.edu.ubaa.auth.JwtAuth
import cn.edu.ubaa.auth.JwtErrorDetails
import cn.edu.ubaa.auth.JwtErrorResponse
import cn.edu.ubaa.auth.SessionManager
import cn.edu.ubaa.model.dto.YgdkClockinSubmitResponse
import cn.edu.ubaa.model.dto.YgdkOverviewResponse
import cn.edu.ubaa.model.dto.YgdkRecordsPageResponse
import cn.edu.ubaa.utils.JwtUtil
import cn.edu.ubaa.utils.UpstreamTimeoutException
import com.auth0.jwt.JWT
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class YgdkRoutesTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `GET overview without token returns unauthorized`() = testApplication {
    application { testYgdkApp(YgdkService(clientProvider = { RouteFakeYgdkClient() })) }

    val response = client.get("/api/v1/ygdk/overview")

    assertEquals(HttpStatusCode.Unauthorized, response.status)
    assertTrue(response.bodyAsText().contains("invalid_token"))
    assertTrue(response.bodyAsText().contains("登录状态已失效，请重新登录"))
  }

  @Test
  fun `GET overview and records with token return YGDK data`() = testApplication {
    val fakeClient = RouteFakeYgdkClient()
    application { testYgdkApp(YgdkService(clientProvider = { fakeClient })) }

    val overviewResponse =
        client.get("/api/v1/ygdk/overview") {
          header(HttpHeaders.Authorization, bearerToken("2418"))
        }
    val recordsResponse =
        client.get("/api/v1/ygdk/records?page=1&size=20") {
          header(HttpHeaders.Authorization, bearerToken("2418"))
        }

    assertEquals(HttpStatusCode.OK, overviewResponse.status)
    assertEquals(HttpStatusCode.OK, recordsResponse.status)

    val overview = json.decodeFromString<YgdkOverviewResponse>(overviewResponse.bodyAsText())
    val records = json.decodeFromString<YgdkRecordsPageResponse>(recordsResponse.bodyAsText())

    assertEquals("阳光体育", overview.classifyName)
    assertEquals("跑步", overview.defaultItemName)
    assertEquals(1, records.content.size)
    assertEquals("操场", records.content.first().place)
  }

  @Test
  fun `POST records with multipart maps request to service`() = testApplication {
    val fakeClient = RouteFakeYgdkClient()
    application {
      testYgdkApp(
          YgdkService(
              clientProvider = { fakeClient },
              nowProvider = { LocalDateTime.parse("2026-04-01T15:30:00") },
          )
      )
    }

    val response =
        client.post("/api/v1/ygdk/records") {
          header(HttpHeaders.Authorization, bearerToken("2418"))
          setBody(
              MultiPartFormDataContent(
                  formData {
                    append("itemId", "2")
                    append("startTime", "2026-04-01 10:00")
                    append("endTime", "2026-04-01 11:00")
                    append("place", "足球场")
                    append("shareToSquare", "true")
                    append(
                        "photo",
                        byteArrayOf(1, 2, 3, 4),
                        Headers.build {
                          append(
                              HttpHeaders.ContentDisposition,
                              "form-data; name=\"photo\"; filename=\"proof.png\"",
                          )
                          append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                        },
                    )
                  }
              )
          )
        }

    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<YgdkClockinSubmitResponse>(response.bodyAsText())
    assertTrue(body.success)
    assertEquals("打卡成功", body.message)
    assertEquals(2, fakeClient.lastClockinItemId)
    assertEquals("健走", fakeClient.lastClockinItemName)
    assertEquals("足球场", fakeClient.lastClockinPlace)
    assertTrue(fakeClient.lastClockinOpen)
    assertNotNull(fakeClient.lastClockinStartAt)
    assertEquals(LocalDateTime.parse("2026-04-01T10:00:00"), fakeClient.lastClockinStartAt)
    assertEquals(LocalDateTime.parse("2026-04-01T11:00:00"), fakeClient.lastClockinEndAt)
    assertContentEquals(byteArrayOf(1, 2, 3, 4), fakeClient.uploadedBytes)
    assertEquals("proof.png", fakeClient.uploadedFileName)
    assertEquals(ContentType.Image.PNG.toString(), fakeClient.uploadedMimeType)
  }

  @Test
  fun `GET overview returns gateway timeout when upstream is too slow`() = testApplication {
    application {
      testYgdkApp(
          YgdkService(
              clientProvider = {
                object : RouteFakeYgdkClient() {
                  override suspend fun getClassifyList(): List<YgdkClassifyRaw> {
                    throw UpstreamTimeoutException("阳光打卡概览加载超时", "ygdk_timeout")
                  }
                }
              }
          )
      )
    }

    val response =
        client.get("/api/v1/ygdk/overview") {
          header(HttpHeaders.Authorization, bearerToken("2418"))
        }

    assertEquals(HttpStatusCode.GatewayTimeout, response.status)
    assertTrue(response.bodyAsText().contains("ygdk_timeout"))
    assertTrue(response.bodyAsText().contains("阳光打卡服务响应超时，请稍后重试"))
    assertTrue(response.bodyAsText().contains("阳光打卡概览加载超时").not())
  }

  private fun Application.testYgdkApp(service: YgdkService) {
    install(ContentNegotiation) { json() }
    install(Authentication) {
      jwt(JwtAuth.JWT_AUTH) {
        verifier(
            JWT.require(JwtUtil.algorithm)
                .withIssuer(JwtUtil.ISSUER)
                .withAudience(JwtUtil.AUDIENCE)
                .build()
        )
        validate { credential -> JWTPrincipal(credential.payload) }
        challenge { _, _ ->
          call.respond(
              HttpStatusCode.Unauthorized,
              JwtErrorResponse(JwtErrorDetails("invalid_token", "登录状态已失效，请重新登录")),
          )
        }
      }
    }
    routing { authenticate(JwtAuth.JWT_AUTH) { ygdkRouting(service) } }
  }

  private fun bearerToken(username: String): String {
    return "Bearer ${JwtUtil.generateToken(username, Duration.ofMinutes(30))}"
  }

  private open class RouteFakeYgdkClient :
      YgdkClient(
          username = "2418",
          sessionManager =
              SessionManager(
                  sessionStore = InMemorySessionStore(),
                  cookieStorageFactory = InMemoryCookieStorageFactory(),
              ),
      ) {
    var uploadedBytes: ByteArray? = null
      private set

    var uploadedFileName: String? = null
      private set

    var uploadedMimeType: String? = null
      private set

    var lastClockinItemId: Int? = null
      private set

    var lastClockinItemName: String? = null
      private set

    var lastClockinPlace: String? = null
      private set

    var lastClockinOpen: Boolean = false
      private set

    var lastClockinStartAt: LocalDateTime? = null
      private set

    var lastClockinEndAt: LocalDateTime? = null
      private set

    override suspend fun getClassifyList(): List<YgdkClassifyRaw> {
      return listOf(
          YgdkClassifyRaw(classifyId = 8, name = "校园活动"),
          YgdkClassifyRaw(classifyId = 3, name = "阳光体育", termNum = 16),
      )
    }

    override suspend fun getItemList(classifyId: Int): List<YgdkItemRaw> {
      return listOf(
          YgdkItemRaw(itemId = 1, name = "跑步", sort = 1),
          YgdkItemRaw(itemId = 2, name = "健走", sort = 2),
      )
    }

    override suspend fun getCheckCount(classifyId: Int): YgdkCountRaw {
      return YgdkCountRaw(termCountShow = 5, termNum = 16)
    }

    override suspend fun getTerm(): YgdkTermRaw = YgdkTermRaw(termId = 20261, name = "2026春")

    override suspend fun getRecords(classifyId: Int, page: Int, limit: Int): YgdkRecordsPageRaw {
      return YgdkRecordsPageRaw(
          records =
              listOf(
                  YgdkRecordRaw(
                      recordId = 9,
                      itemId = 1,
                      itemName = "跑步",
                      startTime = 1_743_465_600,
                      endTime = 1_743_469_200,
                      place = "操场",
                      createTimeLabel = "2026-04-01 09:00",
                  )
              ),
          total = 1,
      )
    }

    override suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): YgdkUploadedFileRaw {
      uploadedBytes = bytes
      uploadedFileName = fileName
      uploadedMimeType = mimeType
      return YgdkUploadedFileRaw(fileName = "server-proof.png")
    }

    override suspend fun clockin(
        classifyId: Int,
        itemId: Int,
        itemName: String,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        place: String,
        imageName: String,
        isOpen: Boolean,
        placeType: Int,
    ): YgdkClockinResultRaw {
      lastClockinItemId = itemId
      lastClockinItemName = itemName
      lastClockinPlace = place
      lastClockinOpen = isOpen
      lastClockinStartAt = startAt
      lastClockinEndAt = endAt
      return YgdkClockinResultRaw(recordId = 1200, termCountShow = 6, termNum = 16)
    }
  }
}
