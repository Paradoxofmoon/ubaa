package cn.edu.ubaa.ui.screens.cgyy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 页面加载后注入的「页面内部状态报告」脚本。
 *
 * 周期性通过 console.error('PAYDEBUG ...') 上报页面关键状态（挂载点、document.title、
 * body 文本长度、appMethod 等），App 侧 onConsoleMessage 捕获 PAYDEBUG 后显示在诊断横幅，
 * 用于定位 WebView 内页面渲染到哪一步（尤其是「只闪标题就空白」的场景）。
 */
private val cgyyDiagnosticsJs =
    """
    (function(){
      function report(tag, data){
        try { console.error('PAYDEBUG ' + tag + ' ' + JSON.stringify(data)); } catch(e) {}
      }
      var checks = 0;
      var timer = setInterval(function(){
        checks++;
        try {
          var chingo = document.getElementById('chingo');
          var nav = document.querySelector('.cgNavigation');
          var body = document.body;
          report('st', {
            t: document.title,
            rs: document.readyState,
            ch: chingo ? chingo.children.length : 'absent',
            bl: body ? body.innerText.length : -1,
            bs: body ? (body.innerText || '').slice(0, 50) : '',
            nav: nav ? (nav.innerText || '').slice(0, 30) : 'no-nav',
            am: typeof window.appMethod,
            ap: typeof window.app
          });
        } catch(e) {
          report('err', String(e));
        }
        if (checks >= 6) clearInterval(timer);
      }, 1000);
      // 捕获未处理的 JS 异常
      window.addEventListener('error', function(e){
        report('jserr', String(e.message) + ' @' + e.filename + ':' + e.lineno);
      });
      window.addEventListener('unhandledrejection', function(e){
        report('rej', String(e.reason && e.reason.message || e.reason));
      });
    })();
    """.trimIndent()

/**
 * 体育场馆网页预约屏（方案 A1）。
 *
 * 先静默触发一次运动场(cgyy venue-server)登录，把 `sso_buaa_token` + cgyy 会话
 * cookie 种进 LocalCookieStore；再渲染 WebView 加载官方网页预约页并注入这些 cookie，
 * 让用户在网页内完成 时段选择 + 点选验证码 + 下单 + 同伴 + 支付。
 *
 * 不注入 cookie 直接打开会在手机上遇到「返回数据格式不正确 / 一直加载」——
 * 因为 cgyy 场馆数据接口要求已登录会话。
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
  var webError by remember { mutableStateOf<String?>(null) }

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
          Column(modifier = Modifier.fillMaxSize()) {
            // 诊断横幅：显示 WebView 内 JS/加载错误，帮助定位白屏
            webError?.let { e ->
              androidx.compose.material3.Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                    "⚠ $e",
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
              }
            }
            InAppWebView(
                url = reserveUrl,
                modifier = Modifier.weight(1f),
                domainCookies = ssoCookies,
                // cgyy 移动版 SPA 需移动 Chrome UA + viewport 适配才能正常渲染（否则空白）
                userAgentOverride = mobileChromeUserAgent,
                enableMobileViewport = true,
                onPageError = { webError = it },
                injectJsOnLoad = cgyyDiagnosticsJs,
            )
          }
        }
      }
    }
  }
}
