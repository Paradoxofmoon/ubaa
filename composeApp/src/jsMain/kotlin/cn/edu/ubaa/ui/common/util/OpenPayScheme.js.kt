package cn.edu.ubaa.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** JS(wasm) 回退到 Compose 系统默认 UriHandler。 */
@Composable
actual fun rememberPayOpener(): (String) -> Boolean {
  val uriHandler = LocalUriHandler.current
  return remember(uriHandler) {
    { url: String ->
      runCatching { uriHandler.openUri(url) }.isSuccess
    }
  }
}
