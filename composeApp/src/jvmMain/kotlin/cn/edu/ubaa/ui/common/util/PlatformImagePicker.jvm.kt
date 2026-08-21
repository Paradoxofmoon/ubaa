package cn.edu.ubaa.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files

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
        runCatching {
              val dialog = FileDialog(null as Frame?, "选择图片", FileDialog.LOAD)
              dialog.isVisible = true
              val fileName = dialog.file ?: return
              val directory = dialog.directory ?: return
              val file = File(directory, fileName)
              val bytes = file.readBytes()
              val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
              PickedImage(bytes = bytes, fileName = file.name, mimeType = mimeType)
            }
            .onSuccess(currentOnImagePicked)
            .onFailure { currentOnError(it.message ?: "读取图片失败") }
      }

      override fun capturePhoto() {
        currentOnError("当前平台暂不支持拍照")
      }
    }
  }
}
