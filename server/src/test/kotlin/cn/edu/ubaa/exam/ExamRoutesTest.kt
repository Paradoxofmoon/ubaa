package cn.edu.ubaa.exam

import cn.edu.ubaa.auth.LoginException
import cn.edu.ubaa.auth.UnsupportedAcademicPortalException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ExamRoutesTest {

  @Test
  fun loginExceptionMapsToInvalidToken() {
    val (status, code) = examErrorResponse(LoginException("session expired"))

    assertEquals(HttpStatusCode.Unauthorized, status)
    assertEquals("invalid_token", code)
  }

  @Test
  fun unsupportedPortalMapsToNotImplemented() {
    val (status, code) = examErrorResponse(UnsupportedAcademicPortalException("unsupported portal"))

    assertEquals(HttpStatusCode.NotImplemented, status)
    assertEquals("unsupported_portal", code)
  }

  @Test
  fun genericExceptionMapsToExamError() {
    val (status, code) = examErrorResponse(IllegalStateException("boom"))

    assertEquals(HttpStatusCode.BadGateway, status)
    assertEquals("exam_error", code)
  }
}
