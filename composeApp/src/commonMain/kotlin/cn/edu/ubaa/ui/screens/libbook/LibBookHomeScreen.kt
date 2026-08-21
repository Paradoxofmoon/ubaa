package cn.edu.ubaa.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.screens.bykc.BykcFeatureCard

@Composable
fun LibBookHomeScreen(
    onReserveClick: () -> Unit,
    onBookingsClick: () -> Unit,
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
            title = "预约座位",
            description = "选择楼馆、分区和座位",
            icon = Icons.Default.EventSeat,
            onClick = onReserveClick,
        )
      }
      item {
        BykcFeatureCard(
            title = "我的预约",
            description = "查看座位预约与取消",
            icon = Icons.Default.History,
            onClick = onBookingsClick,
        )
      }
    }
  }
}
