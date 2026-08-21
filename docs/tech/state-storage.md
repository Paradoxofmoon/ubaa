# 状态与存储

UBAA 的状态分为客户端本地状态、服务端会话状态和短期业务缓存。维护时要先判断状态属于哪个层，避免把跨用户或跨模式状态放错位置。

## 客户端本地状态

- 连接模式：`ConnectionModeStore`。
- Token 和 ClientId：按连接模式作用域存储。
- 直连/WebVPN 会话与 Cookie：`LocalAuthSessionStore` 和 `LocalCookieStore`。
- 用户凭据与自动登录开关：`CredentialStore`。
- 功能偏好：公告已读、博雅课程筛选、研讨室预约表单。

## 服务端状态

- Redis Session、Cookie 和 Refresh Token。
- 预登录上下文和分布式锁。
- 登录指标窗口和 Prometheus 指标。

## 业务缓存

服务端会缓存 signin、bykc、cgyy、spoc、judge、libbook、ygdk 等上游客户端上下文，并通过应用停止回调和定时清理释放。希冀本地实现还维护课程和详情缓存，避免重复解析上游页面。

## 来源文件

- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/storage/TokenStore.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/storage/CredentialStore.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/storage/AnnouncementReadStore.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/storage/BykcCourseFilterStore.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/storage/CgyyReservationFormStore.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalConnectionAuth.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalJudgeApi.kt`
- `server/src/main/kotlin/cn/edu/ubaa/auth/session/SessionManager.kt`
- `server/src/main/kotlin/cn/edu/ubaa/auth/session/RedisSessionStore.kt`
- `server/src/main/kotlin/cn/edu/ubaa/auth/session/RefreshTokenStore.kt`
- `server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/metrics/LoginMetrics.kt`
