package cn.edu.ubaa.api

import cn.edu.ubaa.api.local.encryptCgyyOrderPin
import kotlin.test.Test
import kotlin.test.assertEquals

class CgyyOrderPinTest {
  @Test
  fun `encryptCgyyOrderPin matches real front-end vector`() {
    assertEquals("a378ea8af768ad90595c00e050ba8099", encryptCgyyOrderPin(123, 456))
  }

  @Test
  fun `encryptCgyyOrderPin output is lowercase 32 hex`() {
    val hex = encryptCgyyOrderPin(1008, 581)
    assertEquals(32, hex.length)
    assertEquals(hex.lowercase(), hex)
    assertEquals(true, hex.all { it in '0'..'9' || it in 'a'..'f' })
  }
}
