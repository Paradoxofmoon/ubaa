package cn.edu.ubaa.ui.screens.sport

import androidx.compose.ui.graphics.ImageBitmap
import cn.edu.ubaa.api.local.CgyyCaptchaImageData

/** 把验证码解码像素转成 Compose ImageBitmap（Android 用系统 Bitmap，其余平台占位）。 */
internal expect fun cgyyCaptchaImageBitmap(data: CgyyCaptchaImageData): ImageBitmap