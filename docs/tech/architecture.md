# 架构总览

UBAA 是 Kotlin Multiplatform 全栈同仓项目。核心代码分为 `shared`、`composeApp`、`server`、`androidApp` 和 `iosApp`，其中 `shared` 是前后端契约和跨平台业务接入层的中心；`buildSrc` 保存发布辅助 Gradle 任务。

## 分层

- `composeApp`：Compose Multiplatform UI，覆盖 Desktop、Android、iOS、JS/Wasm Web 的主要界面和 ViewModel。
- `shared`：DTO、API 契约、连接模式、本地直连/WebVPN 实现、Token 和本地存储。
- `server`：Ktor 网关，负责服务端中转、JWT、Redis 会话、上游适配、指标、公告和版本检查。
- `androidApp`：Android 壳工程，承载 Compose Activity、Android 打包配置和签名配置。
- `iosApp`：iOS 壳工程，承载 SwiftUI 入口和共享 framework。
- `buildSrc`：自定义 Gradle 任务，例如 bhpan 只读验证和 release 资产上传。

## 请求流

1. UI 调用 ViewModel。
2. ViewModel 调用 shared 暴露的业务 API。
3. `DefaultApiFactory` 根据 `ConnectionRuntime.currentMode()` 分发到 relay backend 或 local backend。
4. relay backend 访问 UBAA server 的 `/api/v1/*` 路由；local backend 直接访问校内上游或 WebVPN 包装后的上游地址。
5. DTO 从 shared 返回 UI，错误由 shared 的 `safeApiCall` 和错误映射转换为可展示信息。

## 运行时服务

server 启动后安装 ContentNegotiation、CORS、JWT、CallId、CallLogging、Micrometer 指标和全局错误处理。认证路由之外的业务路由都在 JWT 保护下。后台清理协程定期释放过期会话和各业务服务缓存。

## 来源文件

- `settings.gradle.kts`
- `build.gradle.kts`
- `composeApp/build.gradle.kts`
- `shared/build.gradle.kts`
- `server/build.gradle.kts`
- `buildSrc/src/main/kotlin/cn/edu/ubaa/gradle/UploadLatestReleaseToBhpanTask.kt`
- `server/src/main/kotlin/cn/edu/ubaa/Application.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/core/ApiFactory.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/ConnectionRuntime.kt`
