package cn.edu.ubaa.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPlatformImagePicker(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): PlatformImagePicker {
  val currentOnError by rememberUpdatedState(onError)

  return remember {
    object : PlatformImagePicker {
      override val canCapturePhoto: Boolean = false

      override fun pickImage() {
        currentOnError("iOS 端图片选择暂未接入，请先在其他平台上传")
      }

      override fun capturePhoto() {
        currentOnError("当前平台暂不支持拍照")
      }
    }
  }
}
