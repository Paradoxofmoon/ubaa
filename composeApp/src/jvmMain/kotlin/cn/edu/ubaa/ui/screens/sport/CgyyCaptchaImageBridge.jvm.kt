package cn.edu.ubaa.ui.screens.sport

import androidx.compose.ui.graphics.ImageBitmap
import cn.edu.ubaa.api.local.CgyyCaptchaImageData

internal actual fun cgyyCaptchaImageBitmap(data: CgyyCaptchaImageData): ImageBitmap =
    ImageBitmap(data.width, data.height)
