package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.feature.SportVenueApi
import cn.edu.ubaa.api.local.buildBuaaEduCnDomainCookies
import cn.edu.ubaa.ui.component.InAppWebView

/** cgyy 移动预约页按 UA 区分版本；用移动 Chrome UA 让 SPA 走移动端渲染（否则 WebView 空白）。 */
private const val mobileChromeUserAgent =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

/**
 * 高度修复脚本：WebView 环境下 `100vh` 与根元素 `height:100%` 解析为 0（初始包含块高度为 0）， 导致 `html/body → .fullHeight
 * → #mobilePage` 高度链塌陷、滚动容器只剩 padding 高、 页面内容被裁切（只显示顶部一条）。注入显式像素高度恢复，等价于真浏览器给 html 的视口高度。
 */
private val cgyyHeightFixJs =
    """
    (function(){
      function fix(){
        try {
          var h = window.innerHeight;
          document.documentElement.style.height = h + 'px';
          document.body.style.height = h + 'px';
        } catch(e) {}
      }
      fix();
      window.addEventListener('resize', fix);
      // SPA 若在加载过程中重置样式，前 2 秒内兜底补几次
      var n = 0;
      var timer = setInterval(function(){
        fix();
        if (++n >= 5) clearInterval(timer);
      }, 400);
    })();
    """
        .trimIndent()

/**
 * 体育场馆网页预约屏（方案 A1）。
 *
 * 先静默触发一次运动场(cgyy venue-server)登录，把 `sso_buaa_token` + cgyy 会话 cookie 种进 LocalCookieStore；再渲染
 * WebView 加载官方网页预约页并注入这些 cookie， 让用户在网页内完成 时段选择 + 点选验证码 + 下单 + 同伴 + 支付。
 *
 * 不注入 cookie 直接打开会在手机上遇到「返回数据格式不正确 / 一直加载」—— 因为 cgyy 场馆数据接口要求已登录会话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgyyWebViewReserveScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val reserveUrl = "https://cgyy.buaa.edu.cn/venue/mobileReservation"
  var preparing by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  // 先确保 cgyy 体育会话建立（登录会种 sso_buaa_token 等 cookie），再注入 WebView。
  var ssoCookies by remember { mutableStateOf(buildBuaaEduCnDomainCookies()) }

  LaunchedEffect(Unit) {
    runCatching { SportVenueApi().getVenueSites() }
        .onSuccess { ssoCookies = buildBuaaEduCnDomainCookies() }
        .onFailure { error = "预登录场馆系统失败：${it.message ?: "未知错误"}" }
    preparing = false
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("网页预约场馆") },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )
      },
      modifier = modifier,
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when {
        preparing -> {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        error != null -> {
          Box(
              Modifier.fillMaxSize().padding(16.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        else -> {
          InAppWebView(
              url = reserveUrl,
              modifier = Modifier.fillMaxSize(),
              domainCookies = ssoCookies,
              // cgyy 移动版 SPA：移动 Chrome UA 走移动端渲染；必须关 useWideViewPort（否则
              // clientWidth≈980、rem 基准错乱），并注入像素高度修复 WebView 的 vh 解析为 0 问题。
              userAgentOverride = mobileChromeUserAgent,
              enableMobileViewport = false,
              injectJsOnLoad = cgyyHeightFixJs,
          )
        }
      }
    }
  }
}
