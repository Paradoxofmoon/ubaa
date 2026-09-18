package cn.edu.ubaa.ui.screens.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 功能宫格通用卡片：彩色圆角图标容器 + 顶部对齐内容 + 细描边浅底。 [tone] 决定图标容器配色（0~3，循环使用 primary/secondary/tertiary/neutral
 * 四组）， 普通功能/高级功能主宫格及各二级功能主页共用，保证视觉一致。
 */
@Composable
fun FeatureGridCard(
    title: String,
    description: String,
    icon: ImageVector,
    tone: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val iconTones =
      listOf(
          scheme.primaryContainer to scheme.onPrimaryContainer,
          scheme.secondaryContainer to scheme.onSecondaryContainer,
          scheme.tertiaryContainer to scheme.onTertiaryContainer,
          scheme.surfaceVariant to scheme.onSurfaceVariant,
      )
  val (iconContainer, iconContent) = iconTones[tone % iconTones.size]

  Card(
      modifier = modifier.fillMaxWidth().heightIn(min = 144.dp).clickable { onClick() },
      colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
      border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Surface(
          modifier = Modifier.size(48.dp),
          shape = RoundedCornerShape(14.dp),
          color = iconContainer,
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(26.dp),
              tint = iconContent,
          )
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
