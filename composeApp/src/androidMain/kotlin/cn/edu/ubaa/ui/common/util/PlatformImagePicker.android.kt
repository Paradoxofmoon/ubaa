package cn.edu.ubaa.ui.common.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberPlatformImagePicker(
    onImagePicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): PlatformImagePicker {
  val context = LocalContext.current
  val currentOnImagePicked by rememberUpdatedState(onImagePicked)
  val currentOnError by rememberUpdatedState(onError)

  val pickImageLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.readPickedImage(uri) }
            .onSuccess(currentOnImagePicked)
            .onFailure { currentOnError(it.message ?: "读取图片失败") }
      }

  val takePhotoLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        runCatching { bitmap.toPickedImage() }
            .onSuccess(currentOnImagePicked)
            .onFailure { currentOnError(it.message ?: "拍照失败") }
      }

  return remember {
    object : PlatformImagePicker {
      override val canCapturePhoto: Boolean = true

      override fun pickImage() {
        pickImageLauncher.launch("image/*")
      }

      override fun capturePhoto() {
        takePhotoLauncher.launch(null)
      }
    }
  }
}

private fun Context.readPickedImage(uri: Uri): PickedImage {
  val bytes =
      contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IllegalStateException("无法读取所选图片")
  val fileName = resolveFileName(uri) ?: "picked_${System.currentTimeMillis()}.jpg"
  val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
  return PickedImage(bytes = bytes, fileName = fileName, mimeType = mimeType)
}

private fun Context.resolveFileName(uri: Uri): String? {
  return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
  }
}

private fun Bitmap.toPickedImage(): PickedImage {
  val output = ByteArrayOutputStream()
  if (!compress(Bitmap.CompressFormat.JPEG, 92, output)) {
    throw IllegalStateException("无法处理拍摄图片")
  }
  return PickedImage(
      bytes = output.toByteArray(),
      fileName = "camera_${System.currentTimeMillis()}.jpg",
      mimeType = "image/jpeg",
  )
}
