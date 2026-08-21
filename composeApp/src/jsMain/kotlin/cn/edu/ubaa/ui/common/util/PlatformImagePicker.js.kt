package cn.edu.ubaa.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.js.unsafeCast
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
actual fun rememberPlatformImagePicker(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): PlatformImagePicker {
  val currentOnImagePicked by rememberUpdatedState(onImagePicked)
  val currentOnError by rememberUpdatedState(onError)

  return remember {
    object : PlatformImagePicker {
      override val canCapturePhoto: Boolean = false

      override fun pickImage() {
        openWebImagePicker(currentOnImagePicked, currentOnError)
      }

      override fun capturePhoto() {
        currentOnError("当前平台暂不支持拍照")
      }
    }
  }
}

private fun openWebImagePicker(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
) {
  val input = document.createElement("input") as HTMLInputElement
  input.type = "file"
  input.accept = "image/*"
  input.onchange = {
    val file = input.files?.item(0)
    if (file == null) {
      onError("未选择图片")
    } else {
      val reader = FileReader()
      reader.onload = {
        val result = reader.result
        if (result == null) {
          onError("读取图片失败")
        } else {
          val array = Int8Array(result.unsafeCast<ArrayBuffer>())
          val bytes = ByteArray(array.length) { index -> array[index].toByte() }
          onImagePicked(
              PickedImage(
                  bytes = bytes,
                  fileName = file.name,
                  mimeType = (file.type ?: "").ifBlank { "application/octet-stream" },
              )
          )
        }
        null
      }
      reader.onerror = {
        onError("读取图片失败")
        null
      }
      reader.readAsArrayBuffer(file)
    }
    null
  }
  input.click()
}
