package cn.edu.ubaa.ui.common.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android: 用显式 ACTION_VIEW Intent 唤起支付 App（如 weixin:// / alipays://）。
 * Compose 的 uriHandler.openUri 在华为 EMUI 上对自定义 scheme 唤起不稳定，
 * 这里直接构造 Intent 并添加 NEW_TASK 标志，捕获 ActivityNotFoundException。
 */
@Composable
actual fun rememberPayOpener(): (String) -> Boolean {
  val context = LocalContext.current
  return remember(context) {
    { url: String ->
      runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
      }.isSuccess
    }
  }
}
