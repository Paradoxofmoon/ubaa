package cn.edu.ubaa.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

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
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("此功能需要在浏览器中打开", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { uriHandler.openUri(url) }) {
            Text("在浏览器中打开")
        }
    }
}
