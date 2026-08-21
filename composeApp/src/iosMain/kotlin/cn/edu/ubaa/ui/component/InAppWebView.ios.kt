package cn.edu.ubaa.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun InAppWebView(
    url: String,
    modifier: Modifier,
    injectJsOnLoad: String?,
    cookies: List<String>,
    domainCookies: List<Pair<String, String>>,
    onSchemeUrl: ((String) -> Boolean)?,
    onPageError: ((String) -> Unit)?,
    htmlContent: String?,
    userAgentOverride: String?,
    enableMobileViewport: Boolean,
) {
    LaunchedEffect(url) {
        val nsUrl = NSURL.URLWithString(url) ?: return@LaunchedEffect
        UIApplication.sharedApplication.openURL(nsUrl)
    }
}
