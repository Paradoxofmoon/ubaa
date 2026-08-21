package cn.edu.ubaa.bykc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BykcClientTest {

  @Test
  fun `parseBykcSignCourseResponse tolerates empty object data`() {
    val response =
        parseBykcSignCourseResponse(
            """
            {
              "status": "0",
              "errmsg": "请求成功",
              "data": {}
            }
            """
                .trimIndent()
        )

    assertTrue(response.isSuccess)
    assertNotNull(response.data)
  }

  @Test
  fun `parseBykcSignCourseResponse tolerates null data`() {
    val response =
        parseBykcSignCourseResponse(
            """
            {
              "status": "0",
              "errmsg": "请求成功",
              "data": null
            }
            """
                .trimIndent()
        )

    assertTrue(response.isSuccess)
    assertNull(response.data)
  }

  @Test
  fun `parseBykcSignCourseResponse preserves failure message`() {
    val response =
        parseBykcSignCourseResponse(
            """
            {
              "status": "1",
              "errmsg": "当前不在签到时间窗口",
              "data": {}
            }
            """
                .trimIndent()
        )

    assertFalse(response.isSuccess)
    assertEquals("当前不在签到时间窗口", response.errmsg)
  }
}
