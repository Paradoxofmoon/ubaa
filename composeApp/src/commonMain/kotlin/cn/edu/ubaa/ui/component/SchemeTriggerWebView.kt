package cn.edu.ubaa.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.api.local.buildCcpayCookieHeader

/**
 * 可复用的「隐藏 WebView + 注入 JS 自动点支付方式」组件。
 *
 * 用不可见(1dp)的 WebView 加载真实 cc-pay 收银台页，注入 JS 自动点击用户选定的 支付方式(微信/支付宝)，由收银台页自身 JS 触发 scheme 唤起支付 App。
 * 校园卡充值、电费缴费共用。
 *
 * @param cashierUrl cc-pay 收银台地址(不含登录回跳)。
 * @param channel 支付渠道 wx / ali。
 * @param onDiagnose 诊断回调(JS 通过 console.error 'PAYDEBUG' 上报进度)。
 * @param onConsumed 自动点击流程结束后清理回调。
 */
@Composable
fun SchemeTriggerWebView(
    cashierUrl: String,
    channel: String,
    modifier: Modifier = Modifier.size(1.dp),
    onDiagnose: (String) -> Unit = {},
    onConsumed: () -> Unit = {},
    consumeDelayMs: Long = 4500,
) {
  val js = buildAutoClickScript(channel)
  InAppWebView(
      url = cashierUrl,
      modifier = modifier,
      cookies = buildCcpayCookieHeader().split("; ").filter { it.trim().isNotEmpty() },
      injectJsOnLoad = js,
      onSchemeUrl = { target ->
        onDiagnose("唤起支付scheme: ${target.take(160)}")
        false
      },
      onPageError = { msg ->
        // 只上报关键诊断：PAYDEBUG 自动点击结果、页面开始加载(含实际 URL)、主框架加载失败
        if (msg.contains("PAYDEBUG") || msg.contains("页面开始加载") || msg.contains("页面加载失败")) {
          onDiagnose(msg.take(160))
        }
      },
  )
  LaunchedEffect(cashierUrl) {
    kotlinx.coroutines.delay(consumeDelayMs)
    onConsumed()
  }
}

/** 构造注入 JS：轮询等待 Angular 挂载后自动点击目标支付方式。channel: wx / ali。幂等（可提前/重复注入）。 */
fun buildAutoClickScript(channel: String): String {
  val target = if (channel == "ali") "支付宝" else "微信"
  val classKw = if (channel == "ali") "ali" else "wx,weixin"
  return """
    (function(){
      if (window.__PAY_AUTOCLICK_STARTED__) { return; }
      window.__PAY_AUTOCLICK_STARTED__ = true;
      var target='$target';
      var classKw='$classKw';
      function isTarget(e){
        try{
          if(!e) return false;
          var t=(e.textContent||'').trim();
          var c=(e.className&&e.className.toString) ? e.className.toString() : '';
          var lowT=t.toLowerCase(), lowC=c.toLowerCase();
          if(lowT.indexOf(target.toLowerCase())>=0) return true;
          var parts=classKw.split(',');
          for(var i=0;i<parts.length;i++){ if(lowC.indexOf(parts[i])>=0) return true; }
        }catch(err){}
        return false;
      }
      function clickAll(){
        var els=[];
        document.querySelectorAll('li,div,span,button,a,[class]').forEach(function(e){
          if(isTarget(e)) els.push(e);
        });
        if(els.length>0){
          els.slice(0,5).forEach(function(e){
            try{
              e.click();
              ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(type){
                try{ e.dispatchEvent(new MouseEvent(type,{bubbles:true,cancelable:true,view:window})); }catch(err){}
              });
            }catch(err){}
          });
          console.error('PAYDEBUG auto-clicked '+els.length+' for '+target);
          return true;
        }
        return false;
      }
      var tries=0;
      var timer=setInterval(function(){
        tries++;
        var done=clickAll();
        if(done){ clearInterval(timer); }
        else if(tries>=60){ clearInterval(timer); console.error('PAYDEBUG 未能自动定位支付方式'); }
      }, 200);
      setTimeout(function(){ clearInterval(timer); }, 12000);
    })();
  """
      .trimIndent()
}
