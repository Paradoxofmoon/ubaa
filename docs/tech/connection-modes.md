# 连接模式

UBAA 支持三种连接模式：直连、WebVPN 和服务器中转。模式选择影响认证、Cookie、Token、上游访问方式和可用平台。

## 模式

- `DIRECT`：客户端直接访问校内上游。
- `WEBVPN`：客户端通过北航 WebVPN 访问上游。
- `SERVER_RELAY`：客户端只访问 UBAA server，由 server 访问上游。

## 平台能力

`ConnectionRuntime.availableModes()` 根据 `supportsLocalConnectionModes()` 决定可选模式。当前平台实现如下：

- Android、iOS、JVM/Desktop：`supportsLocalConnectionModes()` 返回 `true`，提供三种模式。
- JS Browser、Wasm JS Browser：`supportsLocalConnectionModes()` 返回 `false`，只提供服务器中转，并自动保存该模式。

## 切换语义

切换模式会调用 `resetSession()`，清理以下状态：

- 所有模式作用域下的 Auth Token 和 ClientId。
- 本地认证会话和 Cookie。
- 自动登录开关。
- shared API 客户端、本地上游客户端、希冀缓存和学期缓存。

## WebVPN URL

本地 WebVPN 访问通过 `LocalWebVpnSupport` 和上游 URL 包装完成。服务端中转使用 `VpnCipher` 处理需要走 VPN 的服务端请求。

## CGYY 例外

普通本地上游请求通过 `localUpstreamUrl()` 决定是否包装 WebVPN URL。CGYY 另有 `localCgyyUpstreamUrl()`，当前始终返回原 URL；代码注释说明 `cgyy.buaa.edu.cn` 可公开访问，因此本地 CGYY 不会因为当前模式是 `WEBVPN` 而走 WebVPN 包装。

## 来源文件

- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/ConnectionRuntime.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/core/ApiFactory.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalConnectionAuth.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalWebVpnSupport.kt`
- `server/src/main/kotlin/cn/edu/ubaa/utils/VpnCipher.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/ConnectionRuntimeTest.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/LocalWebVpnSupportTest.kt`
