package cn.edu.ubaa.spoc

import cn.edu.ubaa.auth.InMemoryCookieStorageFactory
import cn.edu.ubaa.auth.InMemorySessionStore
import cn.edu.ubaa.auth.SessionManager
import cn.edu.ubaa.model.dto.UserData
import cn.edu.ubaa.utils.VpnCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SpocClientTest {
  private val json = Json

  @Test
  fun `get all assignments posts encrypted paged requests and merges all pages`() = runBlocking {
    val originalVpnEnabled = VpnCipher.isEnabled
    VpnCipher.isEnabled = false

    val requestBodies = mutableListOf<String>()
    val queryListCalls = mutableListOf<Pair<String, HttpMethod>>()
    val sessionManager =
        SessionManager(
            sessionStore = InMemorySessionStore(),
            cookieStorageFactory = InMemoryCookieStorageFactory(),
            clientFactory = { _: CookiesStorage ->
              HttpClient(MockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                engine {
                  addHandler { request ->
                    when (request.url.encodedPath) {
                      "/spocnewht/cas" ->
                          respond(
                              content = "",
                              status = HttpStatusCode.Found,
                              headers =
                                  headersOf(
                                      HttpHeaders.Location,
                                      "https://spoc.buaa.edu.cn/spocnew/cas?token=test-token&refreshToken=test-refresh",
                                  ),
                          )
                      "/spocnewht/sys/casLogin" -> {
                        assertEquals("Inco-test-token", request.headers["Token"])
                        respond(
                            content = """{"code":200,"content":{"jsdm":"01"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                      }
                      "/spocnewht/inco/ht/queryListByPage" -> {
                        queryListCalls += request.url.encodedPath to request.method
                        val bodyText = (request.body as TextContent).text
                        requestBodies += bodyText
                        val encryptedParam =
                            json
                                .parseToJsonElement(bodyText)
                                .jsonObject["param"]!!
                                .jsonPrimitive
                                .content
                        val plainText = SpocCrypto.decryptParam(encryptedParam)
                        respond(
                            content =
                                when {
                                  """"pageNum":1""" in plainText ->
                                      """{"code":200,"content":{"pageNum":1,"pageSize":15,"pages":2,"hasNextPage":true,"list":[{"zyid":"a1","tjzt":"未做","zyjzsj":"2026-03-31T15:59:59.000+00:00","zymc":"练习题作业1","zykssj":"2026-03-24T08:00:00.000+00:00","sskcid":"course-1","kcmc":"操作系统","mf":"满分:0"}]}}"""
                                  """"pageNum":2""" in plainText ->
                                      """{"code":200,"content":{"pageNum":2,"pageSize":15,"pages":2,"hasNextPage":false,"list":[{"zyid":"a2","tjzt":"已做","zyjzsj":"2026-03-19T16:00:00.000+00:00","zymc":"lab0实验作业","zykssj":"2026-03-16T08:00:00.000+00:00","sskcid":"course-1","kcmc":"操作系统","mf":"满分:100"}]}}"""
                                  else -> error("Unexpected page payload: $plainText")
                                },
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                      }
                      else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                  }
                }
              }
            },
        )

    try {
      val candidate = sessionManager.prepareSession("24182104")
      sessionManager.commitSession(candidate, UserData("Test User", "24182104"))

      val client = SpocClient(username = "24182104", sessionManager = sessionManager)
      val assignments = client.getAllAssignments("2025-20262")

      assertEquals(2, assignments.size)
      assertEquals(
          listOf(
              "/spocnewht/inco/ht/queryListByPage" to HttpMethod.Post,
              "/spocnewht/inco/ht/queryListByPage" to HttpMethod.Post,
          ),
          queryListCalls,
      )
      assertTrue(requestBodies.all { "\"param\"" in it })
      assertTrue(
          requestBodies.any {
            """"pageNum":1""" in
                SpocCrypto.decryptParam(
                    json.parseToJsonElement(it).jsonObject["param"]!!.jsonPrimitive.content
                )
          }
      )
      assertTrue(
          requestBodies.any {
            """"pageNum":2""" in
                SpocCrypto.decryptParam(
                    json.parseToJsonElement(it).jsonObject["param"]!!.jsonPrimitive.content
                )
          }
      )
    } finally {
      sessionManager.close()
      VpnCipher.isEnabled = originalVpnEnabled
    }
  }

  private fun jsonHeaders() =
      headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
