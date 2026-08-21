package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.plantform.PlatformAesCbcNoPadding
import cn.edu.ubaa.model.dto.LibBookEncryptedReserveBody
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object LocalLibBookCrypto {
  private const val ivText = "ZZWBKJ_ZHIHUAWEI"
  private val json = Json { encodeDefaults = true }

  fun encryptReserveRequest(request: LibBookReserveRequest): String =
      encryptReserveBody(
          LibBookEncryptedReserveBody(
              seatId = request.seatId,
              segment = request.segment,
              day = request.day,
              startTime = "",
              endTime = "",
          )
      )

  fun encryptReserveBody(body: LibBookEncryptedReserveBody): String =
      encryptJson(json.encodeToString(body), body.day)

  @OptIn(ExperimentalEncodingApi::class)
  fun encryptJson(plainText: String, day: String): String {
    val key = aesKey(day)
    val iv = ivText.encodeToByteArray()
    val padded = plainText.encodeToByteArray().pkcs7Pad()
    return Base64.encode(PlatformAesCbcNoPadding.encrypt(padded, key, iv))
  }

  private fun aesKey(day: String): ByteArray {
    val digits = day.filter(Char::isDigit)
    require(digits.length == 8) { "Invalid libbook date: $day" }
    return (digits + digits.reversed()).encodeToByteArray()
  }

  private fun ByteArray.pkcs7Pad(blockSize: Int = 16): ByteArray {
    val padLength = blockSize - (size % blockSize)
    return this + ByteArray(padLength) { padLength.toByte() }
  }
}
