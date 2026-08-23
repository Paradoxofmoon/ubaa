package cn.edu.ubaa.ui.screens.sport

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cn.edu.ubaa.api.local.CgyyCaptchaImageData

internal actual fun cgyyCaptchaImageBitmap(data: CgyyCaptchaImageData): ImageBitmap {
  val bitmap = Bitmap.createBitmap(data.width, data.height, Bitmap.Config.ARGB_8888)
  bitmap.setPixels(data.argb, 0, data.width, 0, 0, data.width, data.height)
  return bitmap.asImageBitmap()
}