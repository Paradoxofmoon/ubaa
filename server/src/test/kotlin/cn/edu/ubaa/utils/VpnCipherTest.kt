package cn.edu.ubaa.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class VpnCipherTest {
  @Test
  fun `from vpn url restores standard url with port query and fragment`() {
    val original =
        "https://iclass.buaa.edu.cn:8346/?loginName=abc%2Bdef%3D&type=jumpMyCenter#/MyCenter"
    val wasEnabled = VpnCipher.isEnabled
    VpnCipher.isEnabled = true

    try {
      val vpnUrl = VpnCipher.toVpnUrl(original)

      assertEquals(original, VpnCipher.fromVpnUrl(vpnUrl))
    } finally {
      VpnCipher.isEnabled = wasEnabled
    }
  }
}
