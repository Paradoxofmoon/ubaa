package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.common.util.BackHandlerCompat
import cn.edu.ubaa.ui.screens.sport.cgyyCaptchaImageBitmap

/** 入场验票「预约码」：显示服务端动态二维码 + 到期倒计时自动刷新（网页同款 10s 轮询）。动态码时整屏背景黑↔绿闪烁。 */
@Composable
fun CgyyEntryCodeScreen(
    viewModel: CgyyEntryCodeViewModel,
    onExit: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val dynamic = uiState.code?.isDynamicCode == true
  // 动态码：整屏背景黑↔绿闪烁（网页 codeBox dynamicBg 铺满全屏）；静态码：普通背景
  val flashColor = entryFlashColor(dynamic)

  BackHandlerCompat { onExit() }

  Surface(
      modifier = Modifier.fillMaxSize(),
      color = flashColor ?: MaterialTheme.colorScheme.background,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onExit) {
          Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "返回",
              tint = if (dynamic) Color.White else MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
            "预约码",
            style = MaterialTheme.typography.titleMedium,
            color = if (dynamic) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
      }

      when {
        uiState.isLoading && uiState.code == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        uiState.error != null && uiState.code == null ->
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
              Text(
                  uiState.error.orEmpty(),
                  color = MaterialTheme.colorScheme.error,
                  textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(12.dp))
              Button(onClick = viewModel::load) { Text("重试") }
            }
        else -> EntryCodeBody(uiState, viewModel, dynamic)
      }
    }
  }
}

/**
 * 动态码闪烁色：复刻网页原版 `@keyframes gradientAnimation` `0% {background-color:#000} to
 * {background-color:#2fc707}` + `animation:.5s infinite alternate` —— 黑↔绿每 0.5s 交替闪烁，无限往返。 静态码返回
 * null（调用方用普通背景色）。
 */
@Composable
private fun entryFlashColor(dynamic: Boolean): Color? {
  if (!dynamic) return null
  val transition = rememberInfiniteTransition(label = "entry-code-bg")
  val fraction by
      transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
              infiniteRepeatable(
                  animation = tween(durationMillis = 500, easing = LinearEasing),
                  repeatMode = RepeatMode.Reverse,
              ),
          label = "entry-code-bg-fraction",
      )
  return lerp(Color.Black, Color(0xFF2FC707), fraction)
}

@Composable
private fun EntryCodeBody(
    uiState: CgyyEntryCodeViewModel.UiState,
    viewModel: CgyyEntryCodeViewModel,
    dynamic: Boolean,
) {
  val code = uiState.code
  val qrBitmap = uiState.qrImage
  Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    // 关联订单信息（动态码时白字显示在闪烁背景上）
    code?.orderView?.let { ov ->
      Text(
          buildString {
            append(ov.venueName.orEmpty())
            ov.siteName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = if (dynamic) Color.White else MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(4.dp))
      Text(
          buildString {
            val date = ov.reservationDate
            val detail = ov.reservationDateDetail
            if (!date.isNullOrBlank()) append(date)
            if (!detail.isNullOrBlank()) {
              if (!date.isNullOrBlank()) append(" ")
              append(detail)
            }
          },
          style = MaterialTheme.typography.bodyMedium,
          color =
              if (dynamic) Color.White.copy(alpha = 0.85f)
              else MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(16.dp))
    }

    // 动态码：整屏已在闪烁，这里只放白色二维码框（网页 qrcodeBox 同款，白底保证可扫）
    // 静态码：普通浅灰卡片
    if (qrBitmap != null) {
      val bitmap: ImageBitmap = remember(qrBitmap) { cgyyCaptchaImageBitmap(qrBitmap) }
      Box(
          modifier =
              Modifier.size(300.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                      if (dynamic) {
                        Color.White
                      } else {
                        MaterialTheme.colorScheme.surfaceVariant
                      }
                  )
                  .padding(8.dp),
          contentAlignment = Alignment.Center,
      ) {
        Image(
            bitmap = bitmap,
            contentDescription = "入场预约码",
            modifier = Modifier.fillMaxSize(),
        )
      }
    } else {
      Text(
          if (uiState.isLoading) "正在加载预约码…" else "暂无可用预约码（需有已支付的近期预约）",
          color = if (dynamic) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
    }

    Spacer(Modifier.height(16.dp))

    // 动态码倒计时（到期自动刷新，白字显示在闪烁背景上）
    if (dynamic) {
      val seconds = ((uiState.remainMs + 999) / 1000).toInt()
      Text(
          if (seconds > 0) "$seconds 秒后自动刷新" else "正在刷新…",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
      )
    } else {
      Text(
          "请出示给工作人员扫码验票入场",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(Modifier.height(8.dp))
    Button(onClick = viewModel::load) { Text("手动刷新") }
  }
}
