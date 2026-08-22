# UBAA 去统计上报版（自编译分支）

基于 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（北航校园客户端，MIT 开源）修改的自用版本。

> **本篇 README 是当前开发进度文档。** 记录自 GitHub 原版叉出以来所有改动，重点为 cgyy「体育场馆网页预约」这条优化线的最新进度与**待解决问题**。源码改动全部已提交到 git（HEAD `a14de30`）。

---

## 项目结构（KMP 多平台）

```
ubaa/
├── androidApp/        # Android 应用壳：MainActivity、桌面小组件(NextClass widget)
│   └── src/main/java/cn/edu/ubaa/
│       ├── MainActivity.kt
│       └── android/widget/*.kt
├── composeApp/        # KMP 核心 UI（Compose Multiplatform）
│   └── src/
│       ├── commonMain/kotlin/cn/edu/ubaa/
│       │   ├── ui/screens/cgyy/     # 体育场馆预约（本线全部改动在这）
│       │   │   ├── CgyyHomeScreen.kt
│       │   │   ├── CgyyWebViewReserveScreen.kt   ← 网页预约屏(核心)
│       │   │   ├── CgyyReserveFormScreen.kt
│       │   │   ├── CgyyReservePickerScreen.kt
│       │   │   ├── CgyyOrdersScreen.kt
│       │   │   ├── CgyyLockCodeScreen.kt
│       │   │   ├── CgyyViewModel.kt
│       │   │   └── CgyyUiCommon.kt
│       │   └── ui/component/InAppWebView.kt       ← 跨平台 WebView 接口
│       ├── androidMain/.../ui/component/InAppWebView.android.kt  ← Android 实现
│       ├── iosMain/...   InAppWebView.ios.kt
│       └── jvmMain/...   InAppWebView.jvm.kt
├── shared/            # 共享业务逻辑(登录/会话/API)
│   └── src/commonMain/kotlin/cn/edu/ubaa/api/local/
│       └── LocalConnectionAuth.kt     ← cookie 持久层 + buildBuaaEduCnDomainCookies
├── server/            # 可选服务端
├── settings.gradle.kts
└── build.gradle.kts
```

### 构建产物/调试点

- **CI**：`.github/workflows/android-build.yml`
  - 命令 `./gradlew :androidApp:assembleRelease :androidApp:assembleDebug --no-daemon`
  - 产物 `androidApp/build/outputs/apk/{release,debug}/*.apk`
  - 两个 artifact：`ubaa-release`（签名正式版）、`ubaa-debug`（未签名，可 `chrome://inspect` 连 WebView 远程调试）
- **本地编译**：`./gradlew :androidApp:assembleDebug`
- 需要 JDK 21 + Android SDK

---

## 一、与原版的差异

### 1. 移除登录统计上报
`shared/.../api/auth/LoginStatsReporter.kt` 中 `defaultReporter()` 改为空实现，登录后不再上报学号/连接模式。

### 2. 新增功能（除电力充值外均 ✅ 可用）

| 功能 | 实现方式 | 状态 |
|---|---|---|
| 校园卡余额查询 | CAS SSO → pass.cc-pay.cn API | ✅ 可用 |
| 校园网流量查询 | app.buaa.edu.cn HTML 解析 | ⚠️ 解析待修复 |
| 校园网充值 (zfw) | RSA 登录 + WebView 内嵌充值页 | ✅ 可用 |
| 电费充值 (shsd/cc-pay) | 复用 cc-pay 收银台隐藏 WebView 自动唤起支付 | ✅ 可用 |
| **体育场馆预约 (cgyy)** | 原生过滤可订场 + WebView 完整预约(方案A1) | 🚧 优化中(见下文) |

### 3. 架构重构（渐进式）
- **ServiceRegistry**：轻量客户端工厂
- **InAppWebView**（跨平台）：
  - 参数：`url / injectJsOnLoad / cookies / domainCookies / onSchemeUrl / onPageError / htmlContent / userAgentOverride / enableMobileViewport`
  - Android 用原生 WebView，桌面/iOS 打开系统浏览器
  - 支持**按真实域名注入 Cookie**（`domainCookies: List<Pair<注入域名URL, "name=value">>`），解决跨域会话
  - 支持**覆盖 UA**、**移动视口适配**、**JS 注入**、**console/错误上报诊断**、**自定义 scheme 拦截**（`weixin://`/`alipays://` 唤起支付 App）
- 旧功能仍走 ApiFactory，不受影响

### 4. 构建环境适配
- Gradle 分发走腾讯镜像，Maven 走阿里云镜像（`settings.gradle.kts`）
- JDK 21

---

## 二、cgyy 体育场馆预约 —— 开发进度（当前重点）

> **业务背景**：cgyy = `cgyy.buaa.edu.cn`（北航智慧体育场馆预约）。预约流程走**网页**（时段选择 + 点选验证码 + 下单 + 同伴 + 支付），原生实现全部交互成本太高 → 采用 **UI 原生 + 网页预约混合**（方案 A1）。

### 方案 A1：原生过滤可订场 + WebView 完整预约

```mermaid
graph LR
    A[CgyyHomeScreen 原生首页] --> B[SportVenueApi getVenueSites 列出可订场]
    B --> C[点击"网页预约"]
    C --> D[预登录: SportVenueApi 触发 cgyy 会话]
    D --> E[buildBuaaEduCnDomainCookies 取会话cookie]
    E --> F[InAppWebView 加载 mobileReservation + 注入cookie + 移动UA]
    F --> G[用户在网页内完成时段/验证码/下单/支付]
```

### 关键提交与演进（时间序）

| commit | 改动 | 说明 |
|---|---|---|
| `a2a6150` | feat: 方案A1 | 原生过滤 + WebView 预约，新增 CgyyWebViewReserveScreen / LocalCgyyApi / LocalConnectionAuth |
| `4e0173c` | fix: 两个 bug | ① 双返回键：CGYY_WEBVIEW_RESERVE 隐藏全局 AppTopBar（WebView 屏自带返回键，全屏展示）② 一直加载/系统忙：进 WebView **前**先用 SportVenueApi 触发 cgyy 登录、种 cookie 再注入，避免未登录"返回数据格式不正确" |
| `a48b9a8` | fix: WebView 空白 | 移动 Chrome UA + viewport 适配（SPA 按 UA 区分移动/桌面版） |
| `c0486bf` | chore: 诊断 | WebView 加载 JS/错误横幅辅助定位白屏 |
| `90e097f` / `d823b5c` | fix: 编译 | 补 import、去内部符号冲突 |
| `d3dbb4c` | fix: 白屏 | 修复"双重加载+重组循环重载"导致 SPA 主框架闪标题后空白 |
| `e162291` | fix: 诊断 | 注入页面内部状态诊断 `PAYDEBUG` + 开启 WebView 远程调试（`WebContentsDebugging`）|
| `03f1f2a` | fix: 编译 | `const val` 改 `val`（`trimIndent` 非编译期常量） |
| `a14de30` | **fix: 白屏根因** | **修复 cookie 域名错配**（见下） |

### 🐛 已解决：跳 SSO 登录页（白屏假象） — commit `a14de30`

**现象**：装修复版后诊断输出从"登录页"变为"正常首页"：
- 修复前：`t:"登录-智慧预约管理系统", bl:49`（跳 SSO 登录页）
- 修复后：`t:"首页-智慧预约管理系统", bl:274`，能看到校区/场馆/场地列表

**根因**：`buildBuaaEduCnDomainCookies` 域名错配
- 旧逻辑按 `name.contains("sso")` 把 `sso_buaa_token` 注入 `https://sso.buaa.edu.cn`（发 token 的登录域）
- 但 WebView 加载的是 `https://cgyy.buaa.edu.cn/venue/mobileReservation` → **token 落错域，cgyy 页收不到 → 判定未登录 → 302 到 SSO 登录页**（"闪标题就没了"其实是淡色登录页残影）

**修复**：`buildBuaaEduCnDomainCookies()`（`shared/.../LocalConnectionAuth.kt`）
- 关键：**所有 `.buaa.edu.cn` 域或 `sso_` 开头 cookie 一律注入 `https://cgyy.buaa.edu.cn`**（WebView 目标域），不要按 name 含 "sso" 就塞到 sso 域。
- 去重 `distinctBy { 注入域名 + cookie名 }`。

**诊断经验**（这套排障流程可复用）：
1. WebView"白屏/闪标题"不一定是渲染问题 —— **先判断是否被重定向到登录页**。诊断脚本打印 `document.title + body文本` 一眼即知（`bl:49`=登录页，`bl:274`=首页）。
2. **chrome://inspect 是决定性定位手段**（装 debug APK，真机连 chrome://inspect 看真实 console + DOM）。沙箱浏览器复现不了 cookie 域错配——它带真实有效 token。

### 🔸 诊断脚本（`cgyyDiagnosticsJs`，注入在 CgyyWebViewReserveScreen.kt）

周期性 `console.error('PAYDEBUG ...')` 上报页面状态，App `onConsoleMessage` 捕获 ERROR 级或含 `PAYDEBUG` 的消息 → 顶部诊断横幅显示。字段：
```
st { t: title, rs: readyState, ch: #chingo 子数, bl: body文本长度,
     bs: body文本前50字, nav: .cgNavigation文本, am/app: window.appMethod/app 是否存在 }
jserr / rej   → 未处理 JS 异常 / unhandledrejection
```
每 1s 报一次，最多 6 次。横幅只在 `onPageError` 注入 ERROR 级，避免无害 console（CLodop localhost 探测失败、404）触发重组重载。

### ✅ 当前状态（2026-08-21 真机验证）
- 已登录、正常进入首页，不再跳登录页。
- 手机上报：`t:"场地预约-智慧预约管理系统", rs:"complete", ch:"absent", bl:190`,
  `bs:"测试房间103\n选择日期\n星期六(08/22) 最早于08月20日 12:00可预约\n星期三\n08月..."`，`nav:"测试房间103"`。

### 🚧 待解决（**当前任务**）：页面"只显示一部分"

**现象**：手机 App 里 WebView 进了页面，但只显示一部分（用户原话"页面不对，只显示一部分"）。

**关键排查结论（沙箱浏览器实测）**：
- DOM 里文本是**完整**的：`bl:190` 与浏览器完整渲染一致，内容含「测试房间103 / 选择日期/星期 / 校区 / 场馆 / 场地 / 手机号 / 提交/取消」全部。
- ⇒ **不是数据缺失**，是**视觉/布局问题**。

**根因嫌疑（高度指向）** —— InAppWebView.android.kt 的移动视口设置：
```kt
if (enableMobileViewport) {
  settings.useWideViewPort = true      // ← 高度可疑
  settings.loadWithOverviewMode = true
}
```
cgyy 页面 meta viewport：`width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover`，且是 **rem 自适应 SPA**：
- JS 按 `clientWidth` 动态算 `html{font-size}`（系数 ≈ 3.75；实测 clientWidth=412 时 fs=109.887px）。
- **`useWideViewPort=true` 会忽略 `width=device-width`**，布局视口取"最宽"默认值（约 980px 或更大），导致 `clientWidth` 变成宽视口值 → **rem 基准算错 → 元素尺寸全乱 → 只显示一部分/被裁切**。

浏览器实测佐证：980px 宽视口下 `#mobilePage` `clientHeight==scrollHeight==915`（不滚动）、内容被压扁；412px 时恢复正常。

**建议修复**：
- cgyy 是**移动版 SPA**，需要精确 `width=device-width` 让 JS rem 自适应按真实屏宽计算 ⇒ **对它关掉 `useWideViewPort`/`loadWithOverviewMode`**。
- 该设置是从 cc-pay 收银台（Angular 桌面版）沿用来的，**两个 SPA 需求相反** —— 需要让"移动视口开关"可针对每个页面独立控制（`enableMobileViewport` 已做成参数，但当前 cgyy 屏把它传成了 `true`，应改 `false` 或再细分）。
- 验证方式：真机改后装 debug APK + chrome://inspect 看布局视口宽度是否为真实屏宽，`bl:190` 应保持不变，同时视觉完整。

---

## 三、新功能开发规范

> 目标：后续新功能**不碰上游 7 层抽象**、**复用公共组件**（轮子不重复造）。上游 UBAA 原版代码复杂度高，一律当黑盒——不依赖、不修改其逻辑，避免引入 bug。

### 铁律（必须遵守）
1. **禁止触碰上游 7 层抽象**：不修改、不依赖 ServiceRegistry / ApiFactory / Repository / 核心 ViewModel 等原版数据链；对原版代码当黑盒。
2. **自包含**：新功能 = 自己的数据层 + 状态层 + UI 层（照 electricity / cgyy 的模式）：
   - 数据层：`*Api`（放 `shared/.../api/local/` 或屏目录）
   - 状态层：`*ViewModel`（`UiState` + `viewModelScope`）
   - UI 层：`*Screen`（无状态 Composable + 复用公共组件）
3. **必须复用公共组件**（禁止重复造轮子）：

| 场景 | 用哪个 |
|---|---|
| 金额显示 | `ui/common/util/Money.kt` → `formatMoney(...)` |
| 内嵌网页屏 | `ui/common/components/WebViewContainer.kt` + `ui/component/InAppWebView.kt` |
| 支付跳转 | `ui/component/SchemeTriggerWebView.kt`（不要再手写自动点支付脚本） |
| 加载 / 错误 / 空态 | `ui/common/components/StateContainer.kt` |
| 顶栏 / 导航 | `ui/common/components/AppTopBar.kt` 及统一外壳 |

4. **UI 口径统一**：配色沿用现有功能；间距 / 圆角一律用 `ui/theme/Dimens.kt` 常量（`Dimens.Spacing*` / `Dimens.Radius*`），不写死散值。
5. **提交前自查清单**：
   - [ ] 未触碰上游 7 层抽象（确认无 ServiceRegistry / ApiFactory 引用）
   - [ ] 公共组件已复用（尤其支付跳转 / WebView / 三态 / 金额）
   - [ ] 间距圆角用 `Dimens` 常量
   - [ ] 编译通过（`./gradlew :androidApp:assembleDebug`）
   - [ ] 装包实机验证关键流程

---

## 四、已知其他问题

- 校园网流量页面解析与实际结构不匹配（数据展示不正确）
- 校园网充值登录后 WebView 显示已登录充值页，风格与原生不同（后续可逆向充值 API 改原生）
- 启动时 CAS 登录流程较慢

## 五、调试辅助

- **DebugFileSink**：写调试 HTML/文本到 app cache `ubaa_debug/`，`adb pull /data/user/0/cn.edu.ubaa/cache/ubaa_debug/`
- **PlatformLog**：Android 用 `Log.e`（华为设备会过滤 D 级）

## 六、环境 / 构建

- JDK 21 + Android SDK；Gradle 走腾讯镜像、Maven 走阿里云镜像
- release 签名配置读 `local.properties` 或环境变量 `SIGNING_KEY/SIGNING_STORE_PASSWORD/SIGNING_KEY_ALIAS/SIGNING_KEY_PASSWORD`
- 原 UBAA 仓库：[BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（MIT）