package cn.edu.ubaa.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台应用内 WebView。
 * Android: 原生 WebView，桌面/iOS: 打开系统浏览器。
 *
 * @param url 要加载的页面地址。
 * @param injectJsOnLoad 页面加载完成后注入执行的 JavaScript（如自动填充表单）。仅 Android 生效。
 * @param cookies 注入的 Cookie，格式 "name=value"。仅 Android 生效。
 * @param onSchemeUrl 当 WebView 尝试加载非 http(s) 的自定义 scheme(如 weixin:// / alipays://)时回调，
 *   返回 true 表示该 scheme 已被消费（如交给系统 Intent 唤起支付 App）。仅 Android 生效。
 * @param onPageError 页面加载出错或 console 报错时回调（用于诊断收银台渲染问题）。仅 Android 生效。
 * @param htmlContent 若非空，则加载该 HTML 字符串而非 [url]（用于触发自定义 scheme 如 weixin://）。仅 Android 生效。
 * @param userAgentOverride 若非空，覆盖 WebView 的 User-Agent（用于让移动版 SPA 正确渲染，如 cgyy）。仅 Android 生效。
 * @param enableMobileViewport 若为 true，启用 useWideViewPort + loadWithOverviewMode 移动端视口适配。仅 Android 生效。
 */
@Composable
expect fun InAppWebView(
    url: String,
    modifier: Modifier = Modifier,
    injectJsOnLoad: String? = null,
    cookies: List<String> = emptyList(),
    domainCookies: List<Pair<String, String>> = emptyList(),
    onSchemeUrl: ((String) -> Boolean)? = null,
    onPageError: ((String) -> Unit)? = null,
    htmlContent: String? = null,
    userAgentOverride: String? = null,
    enableMobileViewport: Boolean = false,
)
