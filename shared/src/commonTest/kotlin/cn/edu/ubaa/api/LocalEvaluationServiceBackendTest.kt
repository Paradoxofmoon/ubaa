package cn.edu.ubaa.api

import cn.edu.ubaa.api.core.DefaultApiFactory
import cn.edu.ubaa.api.local.LocalAuthSession
import cn.edu.ubaa.api.local.LocalAuthSessionStore
import cn.edu.ubaa.api.local.LocalCookieStore
import cn.edu.ubaa.api.local.LocalEvaluationServiceBackend
import cn.edu.ubaa.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.model.dto.UserData
import cn.edu.ubaa.model.evaluation.EvaluationCourse
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalEvaluationServiceBackendTest {
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
            authenticatedAt = "2026-04-20T08:00:00Z",
            lastActivity = "2026-04-20T08:30:00Z",
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
  fun `local evaluation service fetches courses from direct upstream`() = runTest {
    val engine = MockEngine { request ->
      when {
        request.url.host == "spoc.buaa.edu.cn" && request.url.encodedPath == "/pjxt/cas" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath ==
                "/pjxt/personnelEvaluation/listObtainPersonnelEvaluationTasks" -> {
          assertEquals("22373333", request.url.parameters["yhdm"])
          assertEquals(null, request.url.parameters["rwmc"])
          assertEquals(null, request.url.parameters["sfyp"])
          respondJson("""{"code":200,"result":{"list":[{"rwid":"rw1","rwmc":"2026春评教"}]}}""")
        }
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/getQuestionnaireListToTask" -> {
          assertEquals("rw1", request.url.parameters["rwid"])
          assertEquals(null, request.url.parameters["sfyp"])
          respondJson("""{"code":200,"result":[{"wjid":"wj1","wjmc":"教学评价","msid":"2"}]}""")
        }
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/getRequiredReviewsData" -> {
          assertEquals(null, request.url.parameters["sfyp"])
          assertEquals(null, request.url.parameters["xnxq"])
          respondJson(
              """
              {
                "code": 200,
                "result": [
                  {
                    "kcdm": "CS101",
                    "kcmc": "操作系统",
                    "bpmc": "李老师",
                    "bpdm": "T001",
                    "pjrdm": "22373333",
                    "pjrmc": "测试学生",
                    "zdmc": "STID",
                    "ypjcs": 0,
                    "xypjcs": 1,
                    "sxz": "1",
                    "rwh": "rwh-1",
                    "xn": "2025-2026",
                    "xq": "1",
                    "xnxq": "2025-20261",
                    "pjlxid": "2",
                    "sfksqbpj": "1",
                    "yxsfktjst": "0"
                  }
                ]
              }
              """
                  .trimIndent()
          )
        }
        else -> error("Unexpected url: ${request.url}")
      }
    }
    useMockUpstream(engine)

    val result = LocalEvaluationServiceBackend().getAllEvaluations()

    assertTrue(result.isSuccess)
    val response = result.getOrNull()
    assertEquals(1, response?.courses?.size)
    assertEquals(1, response?.progress?.totalCourses)
    assertEquals(1, response?.progress?.pendingCourses)
    assertEquals(0, response?.progress?.evaluatedCourses)
    assertEquals(false, response?.courses?.firstOrNull()?.isEvaluated)
    assertEquals("rw1_wj1_CS101_T001", response?.courses?.firstOrNull()?.id)
    assertEquals("2025-20261", response?.courses?.firstOrNull()?.xnxq)
    assertEquals("2", response?.courses?.firstOrNull()?.msid)
  }

  @Test
  fun `local evaluation service fetches latest pending courses with minimal upstream filters`() =
      runTest {
        val engine = MockEngine { request ->
          when {
            request.url.host == "spoc.buaa.edu.cn" && request.url.encodedPath == "/pjxt/cas" ->
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath ==
                    "/pjxt/personnelEvaluation/listObtainPersonnelEvaluationTasks" -> {
              assertEquals("22373333", request.url.parameters["yhdm"])
              assertEquals("1", request.url.parameters["pageNum"])
              assertEquals("10", request.url.parameters["pageSize"])
              assertEquals(null, request.url.parameters["rwmc"])
              assertEquals(null, request.url.parameters["sfyp"])
              assertEquals(null, request.url.parameters["xnxq"])
              respondJson(
                  """
                  {
                    "code": 200,
                    "result": {
                      "list": [
                        {"rwid": "rw-latest", "rwmc": "最新评教任务"}
                      ]
                    }
                  }
                  """
                      .trimIndent()
              )
            }
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath ==
                    "/pjxt/evaluationMethodSix/getQuestionnaireListToTask" -> {
              assertEquals("rw-latest", request.url.parameters["rwid"])
              assertEquals(null, request.url.parameters["sfyp"])
              assertEquals(null, request.url.parameters["pageNum"])
              assertEquals(null, request.url.parameters["pageSize"])
              respondJson(
                  """
                  {
                    "code": 200,
                    "result": [
                      {"wjid": "wj-latest", "wjmc": "课堂教学评价"}
                    ]
                  }
                  """
                      .trimIndent()
              )
            }
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath == "/pjxt/evaluationMethodSix/getRequiredReviewsData" -> {
              assertEquals("wj-latest", request.url.parameters["wjid"])
              assertEquals(null, request.url.parameters["sfyp"])
              assertEquals(null, request.url.parameters["xnxq"])
              assertEquals(null, request.url.parameters["pageNum"])
              assertEquals(null, request.url.parameters["pageSize"])
              respondJson(
                  """
                  {
                    "code": 200,
                    "result": [
                      {
                        "rwid": "rw-latest",
                        "wjid": "wj-latest",
                        "kcdm": "MATH101",
                        "kcmc": "高等数学",
                        "bpmc": "张老师",
                        "bpdm": "T009",
                        "pjrdm": "22373333",
                        "sxz": "1",
                        "rwh": "rwh-latest",
                        "ypjcs": 0
                      }
                    ]
                  }
                  """
                      .trimIndent()
              )
            }
            else -> error("Unexpected url: ${request.url}")
          }
        }
        useMockUpstream(engine)

        val result = LocalEvaluationServiceBackend().getAllEvaluations()

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(1, response.courses.size)
        assertEquals(1, response.progress.totalCourses)
        assertEquals(1, response.progress.pendingCourses)
        assertEquals(0, response.progress.evaluatedCourses)
        assertEquals("rw-latest_wj-latest_MATH101_T009", response.courses.single().id)
        assertEquals("高等数学", response.courses.single().kcmc)
        assertEquals(null, response.courses.single().xnxq)
      }

  @Test
  fun `local evaluation service marks courses evaluated from upstream ypjcs`() = runTest {
    val engine = MockEngine { request ->
      when {
        request.url.host == "spoc.buaa.edu.cn" && request.url.encodedPath == "/pjxt/cas" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath ==
                "/pjxt/personnelEvaluation/listObtainPersonnelEvaluationTasks" ->
            respondJson("""{"code":200,"result":{"list":[{"rwid":"rw1","rwmc":"2026春评教"}]}}""")
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/getQuestionnaireListToTask" ->
            respondJson("""{"code":200,"result":[{"wjid":"wj1","wjmc":"教学评价"}]}""")
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/getRequiredReviewsData" ->
            respondJson(
                """
                {
                  "code": 200,
                  "result": [
                    {
                      "kcdm": "CS101",
                      "kcmc": "操作系统",
                      "bpmc": "李老师",
                      "bpdm": "T001",
                      "ypjcs": 1
                    },
                    {
                      "kcdm": "CS102",
                      "kcmc": "编译原理",
                      "bpmc": "王老师",
                      "bpdm": "T002",
                      "ypjcs": 0
                    }
                  ]
                }
                """
                    .trimIndent()
            )
        else -> error("Unexpected url: ${request.url}")
      }
    }
    useMockUpstream(engine)

    val result = LocalEvaluationServiceBackend().getAllEvaluations()

    assertTrue(result.isSuccess)
    val response = result.getOrThrow()
    assertEquals(2, response.courses.size)
    assertEquals(1, response.progress.evaluatedCourses)
    assertEquals(1, response.progress.pendingCourses)
    val evaluatedCourse = response.courses.single { it.kcdm == "CS101" }
    val pendingCourse = response.courses.single { it.kcdm == "CS102" }
    assertEquals(true, evaluatedCourse.isEvaluated)
    assertEquals(false, pendingCourse.isEvaluated)
  }

  @Test
  fun `local evaluation service submits evaluation through direct upstream`() = runTest {
    val engine = MockEngine { request ->
      when {
        request.url.host == "spoc.buaa.edu.cn" && request.url.encodedPath == "/pjxt/cas" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/reviseQuestionnairePattern" ->
            respondJson("""{"code":200}""")
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/getQuestionnaireTopic" -> {
          assertEquals("rw1", request.url.parameters["rwid"])
          assertEquals("wj1", request.url.parameters["wjid"])
          assertEquals("CS101", request.url.parameters["kcdm"])
          respondJson(
              """
              {
                "code": 200,
                "result": [
                  {
                    "pjxtWjWjbReturnEntity": {
                      "wjzblist": [
                        {
                          "tklist": [
                            {
                              "tmlx": "1",
                              "tmid": "q1",
                              "tmxxlist": [
                                {"tmxxid": "optA"},
                                {"tmxxid": "optB"}
                              ]
                            }
                          ]
                        }
                      ]
                    },
                    "pjxtPjjgPjjgckb": [
                      {
                        "wjssrwid": "ssrw1",
                        "bprdm": "T001",
                        "bprmc": "李老师",
                        "kcdm": "CS101",
                        "kcmc": "操作系统",
                        "pjfs": "1",
                        "pjid": "pj1",
                        "pjlx": "2",
                        "pjrdm": "22373333",
                        "pjrjsdm": "22373333",
                        "pjrxm": "测试学生",
                        "xnxq": "2025-20261",
                        "sfxxpj": "1"
                      }
                    ],
                    "pjmap": {"source": "test"}
                  }
                ]
              }
              """
                  .trimIndent()
          )
        }
        request.url.host == "spoc.buaa.edu.cn" &&
            request.url.encodedPath == "/pjxt/evaluationMethodSix/submitSaveEvaluation" ->
            respondJson("""{"code":200,"message":"提交成功","result":{}}""")
        else -> error("Unexpected url: ${request.url}")
      }
    }
    useMockUpstream(engine)

    val result =
        LocalEvaluationServiceBackend()
            .submitEvaluations(
                listOf(
                    EvaluationCourse(
                        id = "rw1_wj1_CS101_T001",
                        kcmc = "操作系统",
                        bpmc = "李老师",
                        rwid = "rw1",
                        wjid = "wj1",
                        kcdm = "CS101",
                        bpdm = "T001",
                        pjrdm = "22373333",
                        pjrmc = "测试学生",
                        xnxq = "2025-20261",
                        msid = "2",
                        zdmc = "STID",
                        ypjcs = 0,
                        xypjcs = 1,
                        sxz = "1",
                        rwh = "rwh-1",
                        xn = "2025-2026",
                        xq = "1",
                        pjlxid = "2",
                        sfksqbpj = "1",
                        yxsfktjst = "0",
                    )
                )
            )

    assertEquals(1, result.size)
    assertEquals(true, result.single().success)
    assertEquals("评教成功", result.single().message)
    assertEquals("操作系统", result.single().courseName)
  }

  @Test
  fun `local evaluation service leaves subjective answers empty instead of submitting option ids`() =
      runTest {
        var submitBody: String? = null
        val engine = MockEngine { request ->
          when {
            request.url.host == "spoc.buaa.edu.cn" && request.url.encodedPath == "/pjxt/cas" ->
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath == "/pjxt/evaluationMethodSix/reviseQuestionnairePattern" ->
                respondJson("""{"code":200}""")
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath == "/pjxt/evaluationMethodSix/getQuestionnaireTopic" ->
                respondJson(
                    """
                    {
                      "code": 200,
                      "result": [
                        {
                          "pjxtWjWjbReturnEntity": {
                            "wjzblist": [
                              {
                                "tklist": [
                                  {
                                    "tmlx": "1",
                                    "tmid": "q1",
                                    "tmxxlist": [
                                      {"tmxxid": "optA"},
                                      {"tmxxid": "optB"}
                                    ]
                                  },
                                  {
                                    "tmlx": "6",
                                    "tmid": "q7",
                                    "tmxxlist": [
                                      {"tmxxid": "8290"}
                                    ]
                                  },
                                  {
                                    "tmlx": "6",
                                    "tmid": "q8",
                                    "tmxxlist": [
                                      {"tmxxid": "8291"}
                                    ]
                                  }
                                ]
                              }
                            ]
                          },
                          "pjxtPjjgPjjgckb": [
                            {
                              "wjssrwid": "ssrw1",
                              "bprdm": "T001",
                              "bprmc": "李老师",
                              "kcdm": "CS101",
                              "kcmc": "操作系统",
                              "pjfs": "1",
                              "pjid": "pj1",
                              "pjlx": "2",
                              "pjrdm": "22373333",
                              "pjrjsdm": "22373333",
                              "pjrxm": "测试学生",
                              "xnxq": "2025-20261",
                              "sfxxpj": "1"
                            }
                          ],
                          "pjmap": {"source": "test"}
                        }
                      ]
                    }
                    """
                        .trimIndent()
                )
            request.url.host == "spoc.buaa.edu.cn" &&
                request.url.encodedPath == "/pjxt/evaluationMethodSix/submitSaveEvaluation" -> {
              submitBody = request.bodyText()
              respondJson("""{"code":200,"message":"提交成功","result":{}}""")
            }
            else -> error("Unexpected url: ${request.url}")
          }
        }
        useMockUpstream(engine)

        val result =
            LocalEvaluationServiceBackend()
                .submitEvaluations(
                    listOf(
                        EvaluationCourse(
                            id = "rw1_wj1_CS101_T001",
                            kcmc = "操作系统",
                            bpmc = "李老师",
                            rwid = "rw1",
                            wjid = "wj1",
                            kcdm = "CS101",
                            bpdm = "T001",
                            pjrdm = "22373333",
                            pjrmc = "测试学生",
                            xnxq = "2025-20261",
                        )
                    )
                )

        assertEquals(true, result.single().success)
        val answers =
            Json.parseToJsonElement(submitBody ?: error("submit body missing"))
                .jsonObject["pjjglist"]!!
                .jsonArray
                .single()
                .jsonObject["pjxxlist"]!!
                .jsonArray
        val subjectiveAnswers =
            answers.filter {
              it.jsonObject["wjstid"]?.jsonPrimitive?.contentOrNull in setOf("q7", "q8")
            }
        assertEquals(2, subjectiveAnswers.size)
        subjectiveAnswers.forEach { answer ->
          val answerObject = answer.jsonObject
          assertEquals("6", answerObject["stlx"]?.jsonPrimitive?.contentOrNull)
          assertTrue(
              answerObject["wjstctid"]?.jsonPrimitive?.contentOrNull in setOf("8290", "8291")
          )
          assertEquals(0, answerObject["xxdalist"]?.jsonArray?.size)
        }
        assertFalse(
            subjectiveAnswers.any { answer ->
              answer.jsonObject["xxdalist"]!!.jsonArray.any {
                it.jsonPrimitive.contentOrNull in setOf("8290", "8291")
              }
            }
        )
      }

  private fun useMockUpstream(engine: MockEngine) {
    LocalUpstreamClientProvider.clientFactory = { followRedirects ->
      HttpClient(engine) {
        this.followRedirects = followRedirects
        install(HttpCookies) { storage = LocalCookieStore.storage(ConnectionMode.DIRECT) }
      }
    }
  }
}

private fun io.ktor.client.request.HttpRequestData.bodyText(): String {
  return when (val content = body) {
    is TextContent -> content.text
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> error("Unsupported body type: ${content::class}")
  }
}

private fun MockRequestHandleScope.respondJson(body: String) =
    respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
