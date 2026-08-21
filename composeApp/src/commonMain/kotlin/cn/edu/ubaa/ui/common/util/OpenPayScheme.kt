package cn.edu.ubaa.ui.common.util

import androidx.compose.runtime.Composable

/**
 * 跨平台打开移动支付 scheme（weixin:// / alipays://）的 Composable 提供者。
 *
 * Android 上用显式 ACTION_VIEW Intent 唤起（比 Compose 的 uriHandler.openUri 更可靠，
 * 后者在华为 EMUI 对自定义 scheme 唤起不稳定）。
 * 其他平台回退到系统默认处理。
 *
 * @return (url: String) -> Boolean，返回 true 表示成功发起唤起。
 */
@Composable
expect fun rememberPayOpener(): (String) -> Boolean
