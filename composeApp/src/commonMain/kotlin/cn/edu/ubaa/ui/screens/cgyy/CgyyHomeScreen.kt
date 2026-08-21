package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.screens.bykc.BykcFeatureCard

@Composable
fun CgyyHomeScreen(
    onReserveClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onLockCodeClick: () -> Unit,
    onWebReserveClick: () -> Unit,
    venueLabel: String = "研讨室",
) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.weight(1f),
    ) {
      item {
        BykcFeatureCard(
            title = "预约$venueLabel",
            description = "选择校区、楼栋和时段",
            icon = Icons.Default.DateRange,
            onClick = onReserveClick,
        )
      }
      item {
        BykcFeatureCard(
            title = "我的预约",
            description = "查看状态、详情与取消预约",
            icon = Icons.Default.History,
            onClick = onOrdersClick,
        )
      }
      item {
        BykcFeatureCard(
            title = "查看密码",
            description = "查看门锁密码接口原始返回值",
            icon = Icons.Default.Lock,
            onClick = onLockCodeClick,
        )
      }
      item {
        BykcFeatureCard(
            title = "网页预约场馆",
            description = "进入网页完成完整预约（含支付）",
            icon = Icons.Default.DateRange,
            onClick = onWebReserveClick,
        )
      }
    }
  }
}
