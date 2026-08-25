package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.plantform.LocalCgyyImageData
import cn.edu.ubaa.api.plantform.PlatformAesEcbPkcs5Padding
import cn.edu.ubaa.api.plantform.PlatformImageRasterDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** 点选验证码解码后的像素数据（供原生 UI 渲染验证码图片）。 */
class CgyyCaptchaImageData(
    val width: Int,
    val height: Int,
    val argb: IntArray,
)

@OptIn(ExperimentalEncodingApi::class)
fun decodeCgyyCaptchaImage(base64OrDataUri: String): CgyyCaptchaImageData {
  val payload = base64OrDataUri.substringAfter("base64,", base64OrDataUri)
  val decoded: LocalCgyyImageData = PlatformImageRasterDecoder.decode(Base64.decode(payload))
  return CgyyCaptchaImageData(decoded.width, decoded.height, decoded.argb)
}

@OptIn(ExperimentalEncodingApi::class)
fun encryptCgyyClickWordPointJson(pointJsonData: String, secretKey: String): String {
  val keyBytes = secretKey.encodeToByteArray()
  require(keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32) {
    "Invalid AES key size: ${keyBytes.size}"
  }
  val encrypted = PlatformAesEcbPkcs5Padding.encrypt(pointJsonData.encodeToByteArray(), keyBytes)
  return Base64.encode(encrypted)
}

/**
 * 构造下单用的 captchaVerification（网页同款）： AES-ECB(`token` + "---" + `pointJsonData`, `secretKey`) →
 * Base64。 服务器 /api/captcha/check 不返回该值，需客户端自算。
 */
@OptIn(ExperimentalEncodingApi::class)
fun encryptCgyyClickWordCaptchaVerification(
    token: String,
    pointJsonData: String,
    secretKey: String,
): String = encryptCgyyClickWordPointJson("$token---$pointJsonData", secretKey)
