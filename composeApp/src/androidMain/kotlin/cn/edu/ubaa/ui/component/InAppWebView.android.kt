package cn.edu.ubaa.ui.component

import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 关键：允许第三方 Cookie(跨域请求带 cookie)，否则收银台 socket(rmc.cc-pay.cn)
                // 等跨域请求无会话，被拒为 CORS/network error，导致 Angular 初始化/渲染不完整。
                runCatching {
                  android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
                // 移动版 SPA（如 cgyy 场馆预约）需要覆盖为移动 Chrome UA + 视口适配才能正常渲染。
                if (!userAgentOverride.isNullOrBlank()) {
                  settings.userAgentString = userAgentOverride
                }
                if (enableMobileViewport) {
                  settings.useWideViewPort = true
                  settings.loadWithOverviewMode = true
                }
                // 不覆盖 UA（默认）让收银台页按真实 Android WebView 环境正常渲染（避免支付方式图标/文字空白）。
                // 微信支付唤起依赖网页 JS location.href 触发 weixin://，与 UA 无关。

                fun applyCookies() {
                  if (cookies.isNotEmpty() || domainCookies.isNotEmpty()) {
                    runCatching {
                      val cookieManager = CookieManager.getInstance()
                      if (cookieManager != null) {
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        // 按真实域名注入（关键：CASTGC 需独立注入 sso.buaa.edu.cn，否则跨域无会话）
                        domainCookies.forEach { (domainUrl, cookieStr) ->
                          if (domainUrl.isNotBlank() && cookieStr.isNotBlank()) {
                            cookieManager.setCookie(domainUrl, cookieStr)
                          }
                        }
                        cookies.forEach { cookie -> cookieManager.setCookie(url, cookie) }
                        cookieManager.flush()
                      }
                    }
                  }
                }
                applyCookies()

                webChromeClient =
                    object : android.webkit.WebChromeClient() {
                      override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        val msg = "${message.message()} (${message.lineNumber()})"
                        // 只上报真正 ERROR 级或 PAYDEBUG 诊断消息。
                        // 注意：SPA 页面常有无害 console 消息（本地打印服务 CLodop 探测 localhost 失败、
                        // 可选资源 404 等），宽松匹配会导致 onPageError 频繁触发 → 状态更新 → 重组 → 页面被反复重载。
                        if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR ||
                            msg.contains("PAYDEBUG")) {
                          // 忽略 localhost 探测失败（CLodop 打印控件未安装属正常情况）
                          if (msg.contains("localhost") || msg.contains("127.0.0.1")) {
                            return super.onConsoleMessage(message)
                          }
                          onPageError?.invoke(msg.take(300))
                        }
                        return super.onConsoleMessage(message)
                      }
                      override fun onJsAlert(
                          view: WebView?,
                          url: String?,
                          message: String?,
                          result: android.webkit.JsResult?,
                      ): Boolean {
                        message?.let { onPageError?.invoke("JS:${it.take(200)}") }
                        result?.confirm()
                        return true
                      }
                    }

                webViewClient =
                    object : WebViewClient() {
                      override fun onReceivedError(
                          view: WebView,
                          request: WebResourceRequest,
                          error: android.webkit.WebResourceError,
                      ) {
                        val target = request.url.toString()
                        // 忽略本地打印服务探测失败（CLodop localhost 脚本，未安装属正常）
                        if (target.contains("localhost") || target.contains("127.0.0.1")) {
                          return
                        }
                        // 资源级错误（非主框架）不打断页面，只上报诊断
                        if (target.contains("bundle.js") || target.contains(".js")) {
                          onPageError?.invoke("加载JS失败: ${error.errorCode} ${error.description} $target")
                        }
                      }

                      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        val isHttp = target.startsWith("http://") || target.startsWith("https://")
                        if (!isHttp) {
                          // 截获 weixin:// / alipays:// 等自定义 scheme，交给系统 Intent 唤起支付 App
                          val handled = onSchemeUrl?.invoke(target) ?: false
                          if (!handled) {
                            runCatching {
                              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                              context.startActivity(intent)
                            }
                          }
                          return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                      }

                      override fun onPageFinished(view: WebView, loadedUrl: String) {
                        super.onPageFinished(view, loadedUrl)
                        injectJsOnLoad?.takeIf { it.isNotBlank() }?.let { js ->
                          view.evaluateJavascript(js, null)
                        }
                      }
                    }
                // 注意：不在此处加载页面——由下方 update 回调统一加载（首次组合后必被调用一次），
                // 避免 factory 加载 + update 首次调用造成双重加载、打断 SPA 渲染。
            }
        },
        modifier = modifier,
        update = { view ->
          // 关键：仅当目标加载内容变化时才重新加载，避免 Compose 重组（如 onPageError 更新的错误横幅、
          // 父级状态变化）导致 WebView 反复重载整个页面——这会中断 SPA 渲染，表现为主框架"闪标题后空白"。
          val targetContent = if (htmlContent.isNullOrBlank()) url else "html:" + htmlContent
          val loadedContent = view.getTag() as? String
          if (loadedContent != targetContent) {
            view.setTag(targetContent)
            if (!htmlContent.isNullOrBlank()) {
              view.loadDataWithBaseURL(url, htmlContent, "text/html; charset=utf-8", "UTF-8", null)
            } else {
              view.loadUrl(url)
            }
          }
        },
    )
}
