# 课程签到

课程签到模块查询当天可签到课程，并提交签到动作。它依赖上游课堂签到状态，属于写操作功能，必须保留服务端和本地实现的一致错误语义。

## 用户能力

- 查看当天签到课程和当前签到状态。
- 对可签到课程提交签到。
- 刷新当天签到状态。

## 技术路径

- `SigninViewModel` 管理当天签到状态和提交结果。
- `SigninApiBackend` 暴露 today 和 do 两个动作。
- `LocalSigninApiBackend` 在本地连接模式下复用本地 SSO Cookie，先访问 iclass MyCenter 跳转页提取 `loginName`，再访问上游签到接口。
- `SigninService` 在服务端维护上游客户端上下文，服务端实现同样先通过已登录 SSO 会话获取 `loginName`，应用停止和定时清理时释放缓存。

## 接口

- `GET /api/v1/signin/today`
- `POST /api/v1/signin/do`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/signin/SigninScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/signin/SigninViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/SigninApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/SigninLoginNameSupport.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalSigninApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Signin.kt`
- `server/src/main/kotlin/cn/edu/ubaa/signin/SigninRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/signin/SigninService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/signin/SigninClient.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/SigninLoginNameSupportTest.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/LocalSigninApiBackendTest.kt`
- `shared/src/jvmTest/kotlin/cn/edu/ubaa/api/LocalSigninRealIntegrationTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/signin/SigninClientTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/utils/VpnCipherTest.kt`
