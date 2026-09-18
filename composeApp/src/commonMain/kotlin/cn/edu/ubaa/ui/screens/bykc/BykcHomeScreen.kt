package cn.edu.ubaa.ui.screens.bykc

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

/** 博雅课程功能主页。 提供进入选课列表、我的课程和统计数据的入口。 */
@Composable
fun BykcHomeScreen(
    onSelectCourseClick: () -> Unit,
    onMyCoursesClick: () -> Unit,
    onStatisticsClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
      item {
        FeatureGridCard(
            title = "选择课程",
            description = "浏览可选博雅课程",
            icon = LocalAppIcons.current.List,
            tone = 0,
            onClick = onSelectCourseClick,
        )
      }
      item {
        FeatureGridCard(
            title = "我的课程",
            description = "查看已选/签到签退",
            icon = LocalAppIcons.current.Book,
            tone = 1,
            onClick = onMyCoursesClick,
        )
      }
      item {
        FeatureGridCard(
            title = "课程统计",
            description = "查看学时统计",
            icon = LocalAppIcons.current.BarChart,
            tone = 2,
            onClick = onStatisticsClick,
        )
      }
    }
  }
}
