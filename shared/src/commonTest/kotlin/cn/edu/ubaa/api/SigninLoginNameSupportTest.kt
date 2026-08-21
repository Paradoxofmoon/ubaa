package cn.edu.ubaa.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SigninLoginNameSupportTest {

  @Test
  fun `extract loginName from iclass my center url`() {
    assertEquals(
        "Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=",
        extractSigninLoginNameFromUrl(
            "https://iclass.buaa.edu.cn:8346/?loginName=Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=&type=jumpMyCenter#/MyCenter"
        ),
    )
  }

  @Test
  fun `extract loginName preserves encoded base64 characters`() {
    assertEquals(
        "abc+def/ghi=",
        extractSigninLoginNameFromUrl(
            "https://iclass.buaa.edu.cn:8346/?type=jumpMyCenter&loginName=abc%2Bdef%2Fghi%3D"
        ),
    )
  }

  @Test
  fun `extract loginName returns null when parameter is missing`() {
    assertNull(extractSigninLoginNameFromUrl("https://iclass.buaa.edu.cn:8346/?type=jumpMyCenter"))
  }

  @Test
  fun `extract loginName accepts lowercase parameter`() {
    assertEquals(
        "Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=",
        extractSigninLoginNameFromUrl(
            "https://iclass.buaa.edu.cn:8346/?loginname=Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=&type=jumpMyCenter#/MyCenter"
        ),
    )
  }

  @Test
  fun `extract loginName accepts relative redirect url`() {
    assertEquals(
        "Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=",
        extractSigninLoginNameFromUrl(
            "/?loginName=Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg=&type=jumpMyCenter#/MyCenter"
        ),
    )
  }

  @Test
  fun `resolve signin redirect url accepts absolute location`() {
    assertEquals(
        "https://sso.buaa.edu.cn/login",
        resolveSigninRedirectUrl(
            "https://iclass.buaa.edu.cn:8346/?type=jumpMyCenter",
            "https://sso.buaa.edu.cn/login",
        ),
    )
  }

  @Test
  fun `resolve signin redirect url preserves iclass port for root relative location`() {
    assertEquals(
        "https://iclass.buaa.edu.cn:8346/?type=jumpMyCenter#/MyCenter",
        resolveSigninRedirectUrl(
            "https://iclass.buaa.edu.cn:8346/cas-login?ticket=ST-test",
            "/?type=jumpMyCenter#/MyCenter",
        ),
    )
  }

  @Test
  fun `resolve signin redirect url resolves path relative location`() {
    assertEquals(
        "https://iclass.buaa.edu.cn:8346/cas/final",
        resolveSigninRedirectUrl(
            "https://iclass.buaa.edu.cn:8346/cas-login?ticket=ST-test",
            "cas/final",
        ),
    )
  }
}
