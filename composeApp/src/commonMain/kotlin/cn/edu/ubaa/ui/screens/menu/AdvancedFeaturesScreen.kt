package cn.edu.ubaa.ui.screens.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.icons.AppIconSet
import cn.edu.ubaa.ui.icons.LocalAppIcons

internal data class AdvancedFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

internal fun advancedFeatureItems(icons: AppIconSet): List<AdvancedFeatureItem> =
    listOf(
        AdvancedFeatureItem(
            id = "cgyy",
            title = "研讨室预约",
            description = "查询、提交和管理研讨室预约",
            icon = icons.DateRange,
        ),
        AdvancedFeatureItem(
            id = "venue",
            title = "场馆预约",
            description = "网页预约场馆（含支付）",
            icon = icons.Place,
        ),
        AdvancedFeatureItem(
            id = "bus",
            title = "智慧校车",
            description = "校车班次查询与订票",
            icon = icons.DirectionsBus,
        ),
        AdvancedFeatureItem(
            id = "ygdk",
            title = "阳光打卡",
            description = "查看记录并提交体育活动打卡",
            icon = icons.WbSunny,
        ),
        AdvancedFeatureItem(
            id = "evaluation",
            title = "自动评教",
            description = "一键完成学期末评教任务",
            icon = icons.AssignmentTurnedIn,
        ),
        AdvancedFeatureItem(
            id = "more",
            title = "更多功能",
            description = "更多高级功能正在开发中...",
            icon = icons.MoreHoriz,
        ),
    )

@Composable
fun AdvancedFeaturesScreen(
    onCgyyClick: () -> Unit,
    onVenueClick: () -> Unit,
    onBusClick: () -> Unit,
    onEvaluationClick: () -> Unit,
    onYgdkClick: () -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
  val features = advancedFeatureItems(LocalAppIcons.current)

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      itemsIndexed(features) { index, feature ->
        FeatureGridCard(
            title = feature.title,
            description = feature.description,
            icon = feature.icon,
            tone = index % 4,
            onClick = {
              when (feature.id) {
                "cgyy" -> onCgyyClick()
                "venue" -> onVenueClick()
                "bus" -> onBusClick()
                "ygdk" -> onYgdkClick()
                "evaluation" -> onEvaluationClick()
              }
            },
        )
      }
    }
  }
}
