# 配置说明

本项目同时有 Gradle、Node、服务端环境变量和 GitHub Actions Secrets。私密配置不得写入仓库。

## 本地配置

- `local.properties`：本机 Android SDK、测试账号、API_ENDPOINT 等本地输入。该文件不提交。
- `.env`：服务端本地环境变量。该文件不提交。
- `gradle.properties`：项目版本和 Gradle 参数，可提交。

## 构建配置

- `project.version` 和 `project.version.code` 来自 `gradle.properties`。
- `shared/build.gradle.kts` 用 BuildKonfig 写入 `VERSION` 和 `API_ENDPOINT`。
- Android、Desktop、Web、Server 构建任务都在 Gradle 子模块中定义。
- docs 使用 `package.json` 和 `package-lock.json` 固定 VitePress 构建依赖。

## 服务端环境变量

- `SERVER_PORT`、`SERVER_BIND_HOST`：服务端监听地址。
- `REDIS_URI`：Redis 地址。
- `CORS_ALLOWED_ORIGINS`：允许的跨域来源。
- `REDIS_HEALTH_TIMEOUT_MS`：Redis readiness 超时时间。
- `UPDATE_DOWNLOAD_URL`：版本检查返回的下载页面；未配置时使用 GitHub Releases。
- `UBAA_SERVER_VERSION`：显式指定服务端版本；未配置时依次回退到系统属性、manifest 或 `gradle.properties`。
- JWT、分布式锁、预登录和刷新 Token 的预算配置集中在 `AuthConfig`。

## GitHub Secrets

文档发布需要：

- `DOCS_SSH_HOST`
- `DOCS_SSH_USER`
- `DOCS_SSH_KEY`
- `DOCS_SSH_PORT`
- `DOCS_DEPLOY_PATH`

应用发布还依赖现有的 `API_ENDPOINT`、签名和 Cloudflare 相关 Secrets。

当前仓库的 GitHub Actions workflow 为 `docs.yml`、`format.yml`、`release.yml`、`test.yml`；不存在独立的 `.github/workflows/upload.yml`。

## 来源文件

- `.gitignore`
- `.env.sample`
- `gradle.properties`
- `shared/build.gradle.kts`
- `server/src/main/kotlin/cn/edu/ubaa/ServerRuntimeConfig.kt`
- `server/src/main/kotlin/cn/edu/ubaa/auth/config/AuthConfig.kt`
- `.github/workflows/release.yml`
- `.github/workflows/format.yml`
- `.github/workflows/test.yml`
- `.github/workflows/docs.yml`
- `package.json`
