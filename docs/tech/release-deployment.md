# 发布与部署

仓库有三类发布：应用 release 构建、Web Wasm 发布、docs 静态站发布。docs 站点由 `dev` 分支 push 触发，构建后通过 SSH rsync 同步到服务器静态目录。

## 应用 Release

`.github/workflows/release.yml` 在 GitHub Release published 或手动触发时构建，并把产物直接上传到当前 Release：

- Android APK。
- Desktop Linux deb。
- Server fat jar。
- Web Wasm zip。
- Web JS zip。
- iOS framework。
- Windows exe。

## Web Wasm 发布

当前没有独立的 `.github/workflows/upload.yml`。Web Wasm 发布在 `.github/workflows/release.yml` 的 `build-web-wasm` job 中完成：

1. 使用 `:composeApp:wasmJsBrowserDistribution` 构建 `composeApp/build/dist/wasmJs/productionExecutable`。
2. 将 Web Wasm 产物压缩为 `UBAA-Web-Wasm-v{VERSION}.zip` 并上传到 GitHub Release。
3. 使用 Cloudflare Wrangler 将同一目录部署到 Pages 项目 `ubaa`，命令显式传入 `--branch=main`。
4. 调用 Cloudflare API 刷新 `app.buaa.team` 缓存。

## Docs 发布

`.github/workflows/docs.yml` 在 `dev` 分支推送或手动触发时执行：

1. Checkout。
2. Setup Node 22。
3. `npm ci`。
4. `npm run docs:build`。
5. 校验 SSH Secrets。
6. 写入 SSH key 并执行 `ssh-keyscan`。
7. 在服务器创建目标目录。
8. `rsync -az --delete docs/.vitepress/dist/` 到 `DOCS_DEPLOY_PATH`。
9. 调用 Cloudflare API 刷新 `www.buaa.team` 缓存。

服务器需要自行配置 Nginx、Caddy 或其他静态文件服务。本仓库只负责同步构建产物。

## bhpan 发布资产任务

根 Gradle 构建脚本注册了两个 bhpan 相关任务：

- `verifyBhpanReadOnly`：读取 `local.properties` 中的 bhpan 配置，做只读认证链验证。
- `uploadLatestReleaseToBhpan`：读取 GitHub 最新 release，选择 `UBAA-*` 资产，删除 bhpan 中已有的同名前缀文件并重新上传。

`uploadLatestReleaseToBhpan` 会改变真实网盘文件，不属于普通构建验证命令；只有在明确授权真实发布/上传时才运行。

## 来源文件

- `.github/workflows/release.yml`
- `.github/workflows/docs.yml`
- `build.gradle.kts`
- `buildSrc/src/main/kotlin/cn/edu/ubaa/gradle/UploadLatestReleaseToBhpanTask.kt`
- `composeApp/src/webMain/resources/index.html`
- `composeApp/src/webMain/resources/sw.js`
- `package.json`
- `package-lock.json`
