package cn.edu.ubaa.auth

import cn.edu.ubaa.model.dto.LoginRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CasParserTest {

  @Test
  fun buildCaptchaLoginParametersIncludesCaptchaResponseAndExecution() {
    val parameters =
        CasParser.buildCaptchaLoginParameters(
            request =
                LoginRequest(
                    username = "24182104",
                    password = "secret",
                    captcha = "abcd",
                ),
            execution = "e1s1",
        )

    assertEquals("24182104", parameters["username"])
    assertEquals("secret", parameters["password"])
    assertEquals("abcd", parameters["captcha"])
    assertEquals("abcd", parameters["captchaResponse"])
    assertEquals("e1s1", parameters["execution"])
  }

  @Test
  fun buildCasLoginParametersUsesExecutionFromPageForCaptchaForm() {
    val html =
        """
        <html>
          <body>
            <form id="fm1" action="/login">
              <input type="hidden" name="execution" value="e1s1" />
              <input type="text" name="username" />
              <input type="password" name="password" />
              <input type="text" name="captchaResponse" />
            </form>
          </body>
        </html>
        """
            .trimIndent()

    val parameters =
        CasParser.buildCasLoginParameters(
            html,
            LoginRequest(
                username = "24182104",
                password = "secret",
                captcha = "abcd",
            ),
        )

    assertEquals("e1s1", parameters["execution"])
    assertEquals("abcd", parameters["captchaResponse"])
  }

  @Test
  fun passwordExpiryWarningPageBuildsIgnoreParameters() {
    val html =
        """
        <html>
          <body>
            <form id="continueForm" action="/login" method="post">
              <div>账号存在安全风险，请修改密码</div>
              <input type="hidden" name="execution" value="e2s2" />
              <button type="submit" name="_eventId" value="ignoreAndContinue">忽略提示</button>
            </form>
          </body>
        </html>
        """
            .trimIndent()

    assertTrue(CasParser.isIgnorablePasswordExpiryPage(html))

    val parameters = CasParser.buildIgnorePasswordExpiryParameters(CasParser.extractExecution(html))

    assertEquals("e2s2", parameters["execution"])
    assertEquals("ignoreAndContinue", parameters["_eventId"])
  }
}
