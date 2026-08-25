package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.plantform.PlatformAesCbcNoPadding

/**
 * 场馆预约 orderPin：提交按钮点击坐标 `clientX,clientY` → AES-128-CBC/PKCS7 → Hex(32)。
 *
 * 算法来自前端 `commonMethods.keySetPassword`：明文如 `"1008,581"`，密钥/IV 为前端 webpack 硬编码常量，输出 32 位小写
 * hex（区别于验证码 pointJson 的 Base64）。
 */
fun encryptCgyyOrderPin(clientX: Int, clientY: Int): String =
    encryptCgyyOrderPin("$clientX,$clientY")

fun encryptCgyyOrderPin(coordinateText: String): String {
  val keyBytes = ORDER_PIN_KEY.encodeToByteArray()
  val ivBytes = ORDER_PIN_IV.encodeToByteArray()
  require(keyBytes.size == 16 && ivBytes.size == 16) { "Invalid orderPin AES key/IV" }
  val padded = coordinateText.encodeToByteArray().pkcs7Padded()
  val encrypted = PlatformAesCbcNoPadding.encrypt(padded, keyBytes, ivBytes)
  return encrypted.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private fun ByteArray.pkcs7Padded(): ByteArray {
  val blockSize = 16
  val padding = blockSize - size % blockSize
  return this + ByteArray(padding) { padding.toByte() }
}

private const val ORDER_PIN_KEY = "c1h2i5n6g2o2k4a7"
private const val ORDER_PIN_IV = "C2H3I4N5G2O3K1E4"
