package cn.edu.ubaa.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局统一的间距 / 圆角 / 尺寸常量。
 *
 * 新功能一律引用这里，避免各屏各自写死散值、风格不一致。
 */
object Dimens {
  // ---- 间距（4dp 基准网格） ----
  val SpacingXxs = 4.dp
  val SpacingXs = 8.dp
  val SpacingSm = 12.dp
  val SpacingMd = 16.dp
  val SpacingLg = 24.dp
  val SpacingXl = 32.dp

  // ---- 圆角 ----
  val RadiusSm = 8.dp
  val RadiusMd = 12.dp
  val RadiusLg = 16.dp

  // ---- 组件尺寸 ----
  val ButtonHeight = 48.dp
  val CardCornerRadius = RadiusMd
}
