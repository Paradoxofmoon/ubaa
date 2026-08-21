# 登录与连接模式

登录模块负责把用户凭据、验证码、预登录上下文、Token 刷新和连接模式绑定在一起。客户端启动时会恢复已保存的连接模式；如果平台只支持服务器中转，则自动选择 `SERVER_RELAY`。

## 用户能力

- Android、iOS、Desktop 支持直连模式、WebVPN 模式和服务器中转模式。
- Web/Wasm 端只暴露服务器中转模式，启动时会自动保存 `SERVER_RELAY`。
- 支持验证码获取、预登录、登录、登录状态检查、Token 刷新和退出登录。
- 切换连接模式时会清理 Token、ClientId、本地会话、Cookie、本地上游客户端和学期缓存，避免跨模式复用旧状态。
- Web/Wasm 平台只暴露服务器中转；Android、iOS、Desktop 可按平台能力使用本地连接模式。

## 技术路径

- `ConnectionRuntime` 决定可用模式、当前模式和切换时的会话清理。
- `DefaultApiFactory` 根据模式把 shared 的业务接口分发到本地实现或 relay 实现。
- 服务器中转通过 Ktor `/api/v1/auth/*` 路由维护服务端会话、Refresh Token、登录指标和验证码。
- 本地直连/WebVPN 由 `LocalAuthServiceBackend` 登录统一认证，并把上游 Cookie 持久化到本地存储。

## 接口

- `POST /api/v1/auth/preload`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/login-stats`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/status`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/captcha/{captchaId}`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/auth/AuthViewModel.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/auth/LoginScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/auth/ConnectionModeSelectionScreen.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/ConnectionRuntime.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/core/ApiFactory.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/auth/AuthApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalConnectionAuth.kt`
- `server/src/main/kotlin/cn/edu/ubaa/auth/api/AuthRoutes.kt`
- `server/src/test/kotlin/cn/edu/ubaa/auth/api/AuthRoutesTest.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/ConnectionRuntimeTest.kt`
