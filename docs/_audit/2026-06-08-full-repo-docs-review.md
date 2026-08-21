# UBAA 全仓库审查与文档同步报告

## 基线

- 分支：`dev`
- 工作区状态：`git -c safe.directory=D:/Code/Kotlin/UBAA status --short` 无输出，审查开始时工作区干净。
- 审查日期：2026-06-08
- 最近提交：`2fba33e (HEAD -> dev, origin/dev) 更新公告`
- 已确认存在的顶层路径：`shared/`、`server/`、`composeApp/`、`androidApp/`、`iosApp/`、`buildSrc/`、`.github/workflows/`、`docs/`。
- 已确认不存在的顶层路径：`harmonyApp/`。
- 当前 workflow：`docs.yml`、`format.yml`、`release.yml`、`test.yml`。
- 审查约束：未运行真实上传、预约、签到或其他有副作用的校园服务操作。

## 代码事实

### 构建、版本与模块

- `settings.gradle.kts` 只包含 `:androidApp`、`:composeApp`、`:server`、`:shared` 四个 Gradle 子模块；仓库当前没有 `harmonyApp/` 顶层目录。
- `gradle/libs.versions.toml` 当前版本：Kotlin `2.3.20`、Compose Multiplatform `1.10.3`、Ktor `3.4.1`、AGP `9.1.0`；Android compile/target SDK 为 `36`，min SDK 为 `24`。
- `gradle.properties` 当前项目版本为 `project.version=1.7.5`、`project.version.code=28`。
- `shared/build.gradle.kts`、`composeApp/build.gradle.kts`、`server/build.gradle.kts` 均使用 JDK 21 toolchain；Android 壳工程使用 Java 21 编译目标。
- `shared` 和 `composeApp` 的平台目标包含 Android、iOS Arm64/iOS Simulator Arm64、JVM、JS Browser、Wasm JS Browser；JS/Wasm 运行时不支持本地连接模式。
- 根 `build.gradle.kts` 注册 `verifyBhpanReadOnly` 和 `uploadLatestReleaseToBhpan`。前者是只读认证探测任务，后者会删除并重新上传网盘中的 `UBAA-*` 发布资产，本次审查未运行。
- `./gradlew.bat tasks --all --console=plain` 已成功列出任务，确认存在 `:shared:jvmTest`、`:server:test`、`:composeApp:jvmTest`、`:composeApp:wasmJsBrowserDistribution`、`:composeApp:jsBrowserDistribution`、`spotlessCheck`、`verifyBhpanReadOnly`、`uploadLatestReleaseToBhpan`。

### 共享层与连接模式

- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/` 保存跨客户端和服务端复用的 DTO；`api/feature/` 保存业务 backend 契约和 relay backend；`api/local/` 保存本地直连/WebVPN backend；`api/storage/` 保存跨平台本地设置。
- `ConnectionRuntime.availableModes()` 在 Android、iOS、JVM 上返回 `DIRECT`、`WEBVPN`、`SERVER_RELAY`；在 JS 和 Wasm JS 上只返回 `SERVER_RELAY`，并自动保存该模式。
- 切换连接模式会调用 `resetSession()`，清空 Auth Token、ClientId、本地认证会话、Cookie、自动登录开关、API client、本地上游 client、希冀缓存和学期缓存。
- `DefaultApiFactory` 对所有业务 API 做模式分发：`DIRECT`/`WEBVPN` 使用 `LocalBackendSet`，`SERVER_RELAY` 使用 relay backend；当前已包含 `libBookApi()`。
- `LocalWebVpnSupport.localUpstreamUrl()` 只在当前模式为 `WEBVPN` 时包装 WebVPN URL；`localCgyyUpstreamUrl()` 当前始终返回原 URL，代码注释说明 CGYY 上游公开可访问，因此本地 CGYY 即使处于 WebVPN 模式也走直连 URL。

### 服务端与路由

- `server/src/main/kotlin/cn/edu/ubaa/Application.kt` 安装 ContentNegotiation、CORS、JWT、CallId、CallLogging、Micrometer 指标和全局错误处理。
- 公开路由包含 `/`、`/metrics`、`/health/live`、`/health/ready`、`/api/v1/app/version`、`/api/v1/app/announcement`、`/api/v1/auth/*`。
- JWT 保护下注册 user、schedule、bykc、exam、grade、signin、classroom、cgyy、evaluation、spoc、judge、libbook、ygdk 业务路由。
- `Application.module()` 定时清理 session、prelogin、signin、bykc、cgyy、spoc、judge、libbook、ygdk 上游客户端或缓存，并绑定 `ubaa.libbook.cache` 等 Prometheus gauge。
- `/api/v1/app/version` 由 `AppVersionService` 实现，服务端版本来源优先级为系统属性、`UBAA_SERVER_VERSION`、manifest、`gradle.properties`，下载地址来自 `UPDATE_DOWNLOAD_URL` 或 GitHub Releases。
- `AppVersionCheckResponse` 当前包含 `latestVersion`、`status`、`updateAvailable`、`downloadUrl`、`releaseNotes`，并保留旧兼容字段 `serverVersion`、`aligned`；客户端 `UpdateService` 只在 `UPDATE_AVAILABLE` 时返回更新响应。

### UI 与功能

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/navigation/MainAppScreen.kt` 是导航中心，已包含 `LIBBOOK_HOME`、`LIBBOOK_RESERVE`、`LIBBOOK_BOOKINGS` 三个图书馆座位页面。
- `RegularFeaturesScreen.kt` 普通功能列表包含课表、考试、成绩、博雅、空教室、SPOC、希冀和图书馆座位。
- `AdvancedFeaturesScreen.kt` 高级功能列表包含研讨室、阳光打卡、自动评教等入口；课程签到入口已从高级功能中隐藏。
- `HomeTodo.kt` 当前聚合博雅、SPOC、希冀、研讨室、签到、阳光打卡待办；没有图书馆座位待办来源。
- 图书馆座位功能已在 UI、shared、本地 backend 和 server relay 中实现：包含楼馆、分区、区域详情、座位、预约、预约列表、取消预约。

### 高风险专项

- CGYY/研讨室：`LocalCgyyApiBackend` 提供场地、目的类型、日期时段、预约提交、订单、取消和门锁码；预约提交与取消是写操作。本地实现使用 `localCgyyUpstreamUrl()`，当前不走 WebVPN 包装；服务端 `CgyyZhjsClient` 也直接访问 CGYY 上游并处理 `sso/manageLogin`。
- Judge/希冀：`LocalJudgeApiBackend` 使用 `localUpstreamUrl()` 支持直连/WebVPN，列表缓存 TTL 为 5 分钟、详情缓存 TTL 为 2 分钟，详情查询并发数为 4，并维护历史/已截止课程跳过记录。
- LibBook/图书馆座位：服务端路由包含 `GET /api/v1/libbook/libraries`、`GET /api/v1/libbook/areas`、`GET /api/v1/libbook/areas/{areaId}`、`GET /api/v1/libbook/areas/{areaId}/seats`、`POST /api/v1/libbook/bookings`、`GET /api/v1/libbook/reservations`、`POST /api/v1/libbook/bookings/{bookingId}/cancel`；预约与取消是写操作。
- 发布与部署：当前 `.github/workflows/` 只有 `docs.yml`、`format.yml`、`release.yml`、`test.yml`。`.github/workflows/upload.yml` 不存在，Web Wasm 构建、Release 资产上传、Cloudflare Pages 部署和 `app.buaa.team` 缓存刷新都在 `release.yml` 的 `build-web-wasm` job 中完成。
- Docs 发布：`docs.yml` 在 `dev` 分支 docs/README/package/workflow 变更时触发，使用 Node 22、`npm ci`、`npm run docs:build`，随后通过 SSH rsync 发布 `docs/.vitepress/dist/`，并刷新 `www.buaa.team` Cloudflare 缓存。

## 文档漂移

以下漂移已在本次文档同步中处理：

- `docs/tech/configuration.md`、`docs/tech/release-deployment.md`、`docs/_audit/source-inventory.md` 曾把不存在的 `.github/workflows/upload.yml` 当作当前 workflow；现已改为记录 `docs.yml`、`format.yml`、`release.yml`、`test.yml`，并说明 Web Wasm 发布由 `release.yml` 处理。
- `docs/tech/configuration.md` 曾称 BuildKonfig 写入 `APP_VERSION`、`VERSION_CODE`；现已改为当前代码中的 `VERSION` 与 `API_ENDPOINT`。
- `docs/index.md` 的“三种连接模式”表述曾容易误导为所有平台都能自由选择；现已说明 Web/Wasm 固定服务器中转，Android、iOS、Desktop 可选择本地连接模式。
- `docs/features/index.md`、`docs/.vitepress/config.mts` 缺少已实现的图书馆座位功能页和侧边栏入口；现已新增 `docs/features/libbook.md` 并加入侧边栏。
- `docs/features/cgyy.md` 曾只说本地 backend 实现直连/WebVPN；现已补充当前 CGYY 本地 URL 总是直连、不走 WebVPN 包装的事实。
- `docs/tech/server-routes.md` 缺少 `/api/v1/libbook/*`；现已补齐具体路由。
- `docs/tech/shared-api.md` 缺少 `LibBookApi.kt`、`LibBook.kt` 分类与来源；现已补齐。
- `docs/tech/state-storage.md` 的服务端业务缓存列表缺少 libbook；现已补齐。
- `docs/announcements/history.md` 未列出已存在的 `2026-06-03-001.md`；现已加入历史页。
- `docs/announcements/index.md` 的公告示例仍使用旧的 `docs.buaa.team`；现按 docs workflow 的 `www.buaa.team` 缓存刷新目标改为 `www.buaa.team`。

未直接改写的漂移/风险：

- `docs/changelog/index.md` 最新条目停在 `v1.7.2`，而 `gradle.properties` 当前版本为 `1.7.5`。本地没有可追溯的缺失 release notes，且任务要求不伪造版本记录，因此未补写版本条目。

## 已修改的文档

- `README.md`：补充图书馆座位等当前功能，并同步仓库目录树中的 `buildSrc/`、`docs/`、`.github/`。
- `docs/index.md`：补充图书馆座位，修正 Web/Wasm 连接模式限制。
- `docs/.vitepress/config.mts`：新增图书馆座位侧边栏入口。
- `docs/features/index.md`：新增图书馆座位总览，修正首页待办来源。
- `docs/features/libbook.md`：新建图书馆座位功能页。
- `docs/features/auth-and-connection.md`：明确 Android/iOS/Desktop 与 Web/Wasm 的连接模式差异。
- `docs/features/cgyy.md`：补充 CGYY 本地实现的直连例外和写操作风险。
- `docs/tech/architecture.md`：补充 `buildSrc` 职责。
- `docs/tech/modules.md`：补充 source set、`harmonyApp/` 不存在和 bhpan Gradle 任务边界。
- `docs/tech/connection-modes.md`：补充平台实际支持与 CGYY 例外。
- `docs/tech/shared-api.md`：补充 LibBook API/DTO 和版本检查兼容字段。
- `docs/tech/server-routes.md`：补充 `/api/v1/libbook/*` 路由。
- `docs/tech/state-storage.md`：补充 libbook 服务端缓存。
- `docs/tech/configuration.md`：修正 BuildKonfig 字段、服务端版本/下载环境变量和当前 workflow 列表。
- `docs/tech/testing.md`：补充本地 docs build、Gradle 追加验证和 bhpan 上传任务风险。
- `docs/tech/release-deployment.md`：移除旧 upload workflow 说法，补充 release.yml Web Wasm 部署和 Cloudflare 缓存刷新。
- `docs/announcements/history.md`：补充 2026-06-03 公告入口。
- `docs/announcements/index.md`：修正公告示例站点域名。
- `docs/_audit/source-inventory.md`：按当前仓库重新统计并清理 `upload.yml` 旧记录。
- `docs/_audit/2026-06-08-full-repo-docs-review.md`：记录本次审查事实、漂移、文档变更、验证和风险。

## 验证记录

- `git -c safe.directory=D:/Code/Kotlin/UBAA status --short`：退出码 0，无输出。
- `git -c safe.directory=D:/Code/Kotlin/UBAA branch --show-current`：退出码 0，输出 `dev`。
- `git -c safe.directory=D:/Code/Kotlin/UBAA log --oneline --decorate --max-count=12`：退出码 0，确认最近 12 条提交。
- `rg --files -g '!**/build/**' -g '!**/.gradle/**' -g '!docs/.vitepress/dist/**' -g '!docs/.vitepress/cache/**' -g '!node_modules/**'`：退出码 0，完成基线文件清单扫描。
- 最终重跑 `npm run docs:build`：退出码 0，VitePress `v2.0.0-alpha.17` 构建成功；输出包含 `@vueuse/core` 中 `/* #__PURE__ */` 注释位置的 Rollup 警告，但未导致构建失败。
- 最终重跑 `git -c safe.directory=D:/Code/Kotlin/UBAA diff --check`：退出码 0；输出仅包含工作区文件未来会被 Git 从 LF 转为 CRLF 的警告，未报告 trailing whitespace 或 conflict marker。
- `./gradlew.bat :shared:jvmTest`：退出码 1，`156 tests completed, 3 failed, 6 skipped`。失败集中在 `LocalCgyyApiBackendTest`：
  - `cgyy api fetches direct upstream data`
  - `cgyy api submits reservation and handles order actions in direct mode`
  - `cgyy api uses webvpn wrapped urls when current mode is webvpn`
- 对 `:shared:jvmTest` 失败的只读调查结论：当前 `LocalCgyyApiBackend.ensureBusinessLogin()` 使用 `LocalUpstreamClientProvider.newClient(...)` 建立 DIRECT cookie 客户端，现有测试 helper 只覆盖 `clientFactory`；同时 `localCgyyUpstreamUrl()` 当前恒等返回原 URL，而测试名和断言仍期望 WebVPN 包装到 `d.buaa.edu.cn`。本次任务只改文档，未擅自修改业务代码或测试代码。
- `./gradlew.bat :server:test`：退出码 0，`BUILD SUCCESSFUL`。
- `./gradlew.bat :composeApp:jvmTest`：退出码 0，`BUILD SUCCESSFUL`。
- `./gradlew.bat spotlessCheck`：退出码 0，`BUILD SUCCESSFUL`。
- `git -c safe.directory=D:/Code/Kotlin/UBAA status --short`：退出码 0，仅显示 README、docs 下文档改动和新增审查/LibBook 文档。
- `git -c safe.directory=D:/Code/Kotlin/UBAA diff -- README.md docs package.json .github build.gradle.kts settings.gradle.kts gradle.properties`：退出码 0，复核改动集中在 README 和 docs；未发现业务代码、生成产物或敏感配置改动。

## 剩余风险

- `:shared:jvmTest` 当前失败，失败点为现有 `LocalCgyyApiBackendTest` 与当前 CGYY 本地实现的 URL/客户端工厂路径不一致；本次按文档任务范围未修改业务代码或测试代码。
- 未执行 `verifyBhpanReadOnly`，因为本次没有明确授权使用真实账号做 live probe。
- 未执行 `uploadLatestReleaseToBhpan`、预约、签到、阳光打卡、评教或其他写操作。
- `docs/changelog/index.md` 与 `gradle.properties` 版本存在潜在不同步，但缺少当前 release notes 的本地事实来源，未伪造更新日志。
