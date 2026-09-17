package cn.edu.ubaa.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前全局图标集。默认 Material Rounded，用户可在设置中切换为 Tabler。
 *
 * 由 App 根节点根据 [cn.edu.ubaa.api.IconSetStore] 的偏好通过 [CompositionLocalProvider] 提供；所有调用点统一读
 * `LocalAppIcons.current`， 切换后全应用即时生效（ImageVector 纯数据，无需重建）。
 */
val LocalAppIcons = staticCompositionLocalOf<AppIconSet> { MaterialRoundedIcons.Set }
