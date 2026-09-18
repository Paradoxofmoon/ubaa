package cn.edu.ubaa.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.icons.LocalAppIcons
import cn.edu.ubaa.ui.screens.menu.FeatureGridCard

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
        FeatureGridCard(
            title = "预约座位",
            description = "选择楼馆、分区和座位",
            icon = LocalAppIcons.current.EventSeat,
            tone = 0,
            onClick = onReserveClick,
        )
      }
      item {
        FeatureGridCard(
            title = "我的预约",
            description = "查看座位预约与取消",
            icon = LocalAppIcons.current.History,
            tone = 1,
            onClick = onBookingsClick,
        )
      }
    }
  }
}
