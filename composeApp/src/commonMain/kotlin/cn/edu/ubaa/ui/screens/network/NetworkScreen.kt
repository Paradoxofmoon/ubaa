package cn.edu.ubaa.ui.screens.network

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NetworkScreen(
    uiState: NetworkUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val pullRefreshState = rememberPullRefreshState(refreshing = uiState.isRefreshing, onRefresh = onRefresh)

  Box(modifier = modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item { Spacer(modifier = Modifier.height(16.dp)) }

      when {
        uiState.isLoading -> {
          item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        uiState.error != null -> {
          item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                    text = "流量加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(onClick = onRetry) { Text("重试") }
              }
            }
          }
        }
        else -> {
          item {
            FreeTrafficCard(
                remaining = uiState.trafficData.freeTrafficRemaining,
                usedTraffic = uiState.trafficData.usedTraffic,
                billingPolicy = uiState.trafficData.billingPolicy,
            )
          }

          uiState.trafficData.paidTrafficRemaining?.let { remaining ->
            item {
              TrafficInfoCard(
                  title = "计费流量剩余",
                  subtitle = "剩余 ${formatGb(remaining)}（不含套餐）",
                  icon = Icons.Default.Paid,
                  isSecondary = true,
              )
            }
          }

          uiState.trafficData.usedSeconds?.let { seconds ->
            item {
              TrafficInfoCard(
                  title = "已用时长",
                  subtitle = formatSeconds(seconds),
                  icon = Icons.Default.Paid,
                  isSecondary = true,
              )
            }
          }

          uiState.trafficData.settleDate?.let { date ->
            item {
              TrafficInfoCard(
                  title = "结算日期",
                  subtitle = date,
                  icon = Icons.Default.CardGiftcard,
                  isSecondary = true,
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    PullRefreshIndicator(
        refreshing = uiState.isRefreshing,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

@Composable
private fun FreeTrafficCard(
    remaining: Double,
    usedTraffic: Double?,
    billingPolicy: String?,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      shape = MaterialTheme.shapes.medium,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
            imageVector = Icons.Default.NetworkWifi,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "免费流量剩余",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
      }

      Text(
          text = formatGb(remaining),
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )

      usedTraffic?.let { used ->
        Text(
            text = "已用 ${formatGb(used)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      billingPolicy?.let { policy ->
        Text(
            text = policy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TrafficInfoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSecondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
  val containerColor =
      if (isSecondary) {
        MaterialTheme.colorScheme.surfaceVariant
      } else {
        MaterialTheme.colorScheme.surface
      }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = containerColor),
      elevation = CardDefaults.cardElevation(defaultElevation = if (isSecondary) 1.dp else 2.dp),
      shape = MaterialTheme.shapes.medium,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
      }

      Text(
          text = subtitle,
          style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
          fontWeight = FontWeight.Bold,
          color = if (isSecondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

private fun formatGb(value: Double): String {
  val scaled = (value * 100).toLong()
  val whole = scaled / 100
  val fraction = kotlin.math.abs(scaled % 100)
  return "$whole.${fraction.toString().padStart(2, '0')} GB"
}

private fun formatSeconds(value: Long): String {
  if (value <= 0) return "0秒"
  val hours = value / 3600
  val minutes = (value % 3600) / 60
  val seconds = value % 60
  return buildString {
    if (hours > 0) append("${hours}小时")
    if (minutes > 0) append("${minutes}分")
    if (seconds > 0 || (hours == 0L && minutes == 0L)) append("${seconds}秒")
  }
}
