package cn.edu.ubaa.libbook

import cn.edu.ubaa.model.dto.LibBookEncryptedReserveBody
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object LibBookCrypto {
  private const val IV_TEXT = "ZZWBKJ_ZHIHUAWEI"
  private val json = Json { encodeDefaults = true }

  fun encryptReserveRequest(request: LibBookReserveRequest): String {
    return encryptReserveBody(
        LibBookEncryptedReserveBody(
            seatId = request.seatId,
            segment = request.segment,
            day = request.day,
            startTime = "",
            endTime = "",
        )
    )
  }

  fun encryptReserveBody(body: LibBookEncryptedReserveBody): String =
      encryptJson(json.encodeToString(body), body.day)

  fun encryptJson(plainText: String, day: String): String {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(aesKey(day), "AES"),
        IvParameterSpec(IV_TEXT.toByteArray()),
    )
    return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.toByteArray().pkcs7Pad()))
  }

  private fun aesKey(day: String): ByteArray {
    val digits = day.filter(Char::isDigit)
    require(digits.length == 8) { "Invalid libbook date: $day" }
    return (digits + digits.reversed()).toByteArray()
  }

  private fun ByteArray.pkcs7Pad(blockSize: Int = 16): ByteArray {
    val padLength = blockSize - (size % blockSize)
    return this + ByteArray(padLength) { padLength.toByte() }
  }
}
