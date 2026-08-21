package cn.edu.ubaa

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.edu.ubaa.runtime.LogConfig

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // 允许通过 chrome://inspect 远程调试 WebView（定位 cgyy 等 SPA 白屏的关键手段）。
    // 仅对可调试构建开启，避免正式发布包暴露调试能力。
    val isDebuggable =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (isDebuggable) {
      runCatching { WebView.setWebContentsDebuggingEnabled(true) }
    }
    LogConfig.enabled = isDebuggable

    setContent { App() }
  }
}
