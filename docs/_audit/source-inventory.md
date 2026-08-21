# 源文件清单

生成时间：2026-06-08，全仓库文档审查期间。

本清单基于当前仓库状态生成，包含 Git 已跟踪文件和本次审查新增的未跟踪文档产物。扫描排除了 `build/`、`.gradle/`、`node_modules/`、`tmp/`、`local.properties`、`docs/.vitepress/dist/` 和 `docs/.vitepress/cache/`。

## 扫描命令

```powershell
$tracked = git -c safe.directory=D:/Code/Kotlin/UBAA ls-files
$untracked = git -c safe.directory=D:/Code/Kotlin/UBAA ls-files --others --exclude-standard
$files = @($tracked + $untracked) | Where-Object {
  $_ -notmatch '(^|/)(build|\.gradle|node_modules|tmp)(/|$)' -and
  $_ -notmatch '^docs/\.vitepress/(dist|cache)/' -and
  $_ -ne 'local.properties'
}
```

## 汇总

- 扫描文件总数：545
- `shared`：161
- `composeApp`：158
- `server`：127
- `docs`：38
- `androidApp`：23
- `iosApp`：14
- `.github`：4
- `buildSrc`：3
- `gradle`：3
- 顶层单文件：`README.md`、`package.json`、`package-lock.json`、`LICENSE`、`kotlin-js-store`、`gradle.properties`、`gradlew`、`settings.gradle.kts`、`build.gradle.kts`、`.vscode`、`.gitignore`、`.gitattributes`、`gradlew.bat`、`.env.sample`

## 顶层模块

| 路径 | 数量 | 当前文档覆盖 | 备注 |
| --- | ---: | --- | --- |
| `shared/` | 161 | `docs/tech/shared-api.md`、功能文档、`docs/tech/connection-modes.md`、`docs/tech/state-storage.md` | DTO、API 契约、relay/local backend、存储、平台能力。 |
| `composeApp/` | 158 | `docs/tech/modules.md`、功能文档 | Compose Multiplatform UI、ViewModel、导航、Web 资源、桌面打包。 |
| `server/` | 127 | `docs/tech/server-routes.md`、`docs/tech/architecture.md`、功能文档 | Ktor 路由、会话和缓存服务、上游客户端、指标、公告、版本检查。 |
| `docs/` | 38 | `docs/index.md` 与 VitePress sidebar | 公共文档、功能页、技术页、公告、审查文件、静态二维码图片。 |
| `androidApp/` | 23 | `README.md`、`docs/tech/modules.md` | Android 壳工程、manifest、资源、签名和版本配置。 |
| `iosApp/` | 14 | `README.md`、`docs/tech/modules.md` | SwiftUI 壳工程、Xcode 项目、图标和 plist。 |
| `buildSrc/` | 3 | `docs/tech/modules.md`、`docs/tech/testing.md`、`docs/tech/release-deployment.md` | 自定义 bhpan Gradle 任务及测试。 |
| `gradle/` | 3 | `docs/tech/configuration.md` | 版本目录和 Gradle wrapper。 |

## Source Set 计数

| 分组 | 数量 |
| --- | ---: |
| `shared/src/commonMain` | 72 |
| `shared/src/commonTest` | 39 |
| `shared/src/androidMain` | 9 |
| `shared/src/iosMain` | 9 |
| `shared/src/jsMain` | 9 |
| `shared/src/jvmMain` | 9 |
| `shared/src/jvmTest` | 4 |
| `shared/src/wasmJsMain` | 9 |
| `composeApp/src/commonMain` | 99 |
| `composeApp/src/commonTest` | 22 |
| `composeApp/src/androidMain` | 3 |
| `composeApp/src/iosMain` | 4 |
| `composeApp/src/jsMain` | 3 |
| `composeApp/src/jvmMain` | 5 |
| `composeApp/src/wasmJsMain` | 3 |
| `composeApp/src/webMain` | 13 |
| `server/src/main` | 79 |
| `server/src/test` | 47 |

## GitHub Workflows

当前 workflow 文件：

- `.github/workflows/docs.yml`
- `.github/workflows/format.yml`
- `.github/workflows/release.yml`
- `.github/workflows/test.yml`

已清理的过期记录：

- `.github/workflows/upload.yml` 当前不存在，不能作为现行 workflow 记录。

## 文档文件

| 路径 | 状态 |
| --- | --- |
| `docs/index.md` | 文档站首页。 |
| `docs/.vitepress/config.mts` | VitePress 导航和侧边栏。 |
| `docs/features/index.md` | 功能总览。 |
| `docs/features/auth-and-connection.md` | 登录与连接模式。 |
| `docs/features/schedule-and-exam.md` | 课表与考试。 |
| `docs/features/grades.md` | 成绩查询。 |
| `docs/features/bykc.md` | 博雅课程。 |
| `docs/features/classroom.md` | 空教室查询。 |
| `docs/features/spoc.md` | SPOC 作业。 |
| `docs/features/judge.md` | 希冀作业。 |
| `docs/features/libbook.md` | 图书馆座位。 |
| `docs/features/signin.md` | 课程签到。 |
| `docs/features/cgyy.md` | 研讨室预约。 |
| `docs/features/ygdk.md` | 阳光打卡。 |
| `docs/features/evaluation.md` | 自动评教。 |
| `docs/tech/architecture.md` | 架构总览。 |
| `docs/tech/modules.md` | 模块职责。 |
| `docs/tech/shared-api.md` | 共享 API 与契约。 |
| `docs/tech/connection-modes.md` | 连接模式规则。 |
| `docs/tech/server-routes.md` | Ktor 路由。 |
| `docs/tech/state-storage.md` | 客户端和服务端状态、缓存。 |
| `docs/tech/configuration.md` | 本地、环境变量和 CI 配置。 |
| `docs/tech/testing.md` | 验证命令和 CI 质量门禁。 |
| `docs/tech/release-deployment.md` | Release、Web 和 docs 部署。 |
| `docs/tech/troubleshooting.md` | 排障指南。 |
| `docs/tech/privacy-policy.md` | 隐私边界。 |
| `docs/tech/disclaimer.md` | 第三方免责声明。 |
| `docs/announcements/index.md` | 公告维护说明。 |
| `docs/announcements/history.md` | 公告历史。 |
| `docs/changelog/index.md` | 更新日志索引。 |
| `docs/_audit/2026-06-08-full-repo-docs-review.md` | 本次审查报告。 |
| `docs/_audit/source-inventory.md` | 本清单。 |

## 高风险源码锚点

| 区域 | 源码锚点 | 覆盖文档 |
| --- | --- | --- |
| 连接模式 | `shared/src/commonMain/kotlin/cn/edu/ubaa/api/ConnectionRuntime.kt`、`shared/src/commonMain/kotlin/cn/edu/ubaa/api/core/ApiFactory.kt`、`shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalWebVpnSupport.kt` | `docs/features/auth-and-connection.md`、`docs/tech/connection-modes.md` |
| CGYY | `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalCgyyApi.kt`、`server/src/main/kotlin/cn/edu/ubaa/cgyy/CgyyRoutes.kt`、`server/src/main/kotlin/cn/edu/ubaa/cgyy/CgyyService.kt`、`server/src/main/kotlin/cn/edu/ubaa/cgyy/CgyyZhjsClient.kt` | `docs/features/cgyy.md`、`docs/tech/connection-modes.md` |
| Judge | `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalJudgeApi.kt`、`server/src/main/kotlin/cn/edu/ubaa/judge/JudgeRoutes.kt`、`server/src/main/kotlin/cn/edu/ubaa/judge/JudgeService.kt`、`server/src/main/kotlin/cn/edu/ubaa/judge/JudgeSupport.kt` | `docs/features/judge.md` |
| LibBook | `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/LibBookApi.kt`、`shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalLibBookApi.kt`、`server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookRoutes.kt`、`server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookService.kt`、`composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/libbook/` | `docs/features/libbook.md`、`docs/tech/server-routes.md`、`docs/tech/shared-api.md` |
| 版本检查 | `shared/src/commonMain/kotlin/cn/edu/ubaa/api/auth/UpdateService.kt`、`server/src/main/kotlin/cn/edu/ubaa/version/AppVersionService.kt`、`server/src/main/kotlin/cn/edu/ubaa/version/AppVersionRoutes.kt` | `docs/tech/shared-api.md`、`docs/tech/configuration.md`、`docs/changelog/index.md` |
| 发布与 docs 部署 | `.github/workflows/release.yml`、`.github/workflows/docs.yml`、`build.gradle.kts`、`buildSrc/src/main/kotlin/cn/edu/ubaa/gradle/UploadLatestReleaseToBhpanTask.kt` | `docs/tech/release-deployment.md`、`docs/tech/testing.md` |

## 备注

- 当前仓库不存在 `harmonyApp/`。
- 当前仓库不存在 `.github/workflows/upload.yml`；Web Wasm 部署由 `.github/workflows/release.yml` 处理。
- `docs/.vitepress/dist/` 和 `docs/.vitepress/cache/` 是生成产物，按计划排除。
- `local.properties` 是本机私密配置，按计划排除。
