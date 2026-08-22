package cn.edu.ubaa.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 统一的三态容器：加载中 / 错误（可带重试）/ 正常内容。
 *
 * 新功能直接复用，避免各屏重复实现 loading / error / empty 态。
 */
@Composable
fun StateContainer(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  when {
    isLoading -> {
      Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    error != null -> {
      Column(
          modifier = modifier.fillMaxSize().padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
      ) {
        Text(
            error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (onRetry != null) {
          Spacer(Modifier.height(12.dp))
          Button(onClick = onRetry) { Text("重试") }
        }
      }
    }
    else -> content()
  }
}
