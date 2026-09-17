package cn.edu.ubaa.ui.common.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.ui.icons.LocalAppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    canNavigateBack: Boolean,
    onNavigationIconClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
  CenterAlignedTopAppBar(
      expandedHeight = 56.dp,
      title = { Text(title) },
      navigationIcon = {
        IconButton(onClick = onNavigationIconClick) {
          if (canNavigateBack) {
            Icon(LocalAppIcons.current.ArrowBack, contentDescription = "返回")
          } else {
            Icon(LocalAppIcons.current.Menu, contentDescription = "菜单")
          }
        }
      },
      actions = actions,
  )
}
