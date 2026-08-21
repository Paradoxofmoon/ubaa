# 博雅课程

博雅课程模块覆盖课程发现、筛选、详情、已选课程、统计、选课、退课和签到签退。它是读写混合模块，所有写操作都需要明确的服务端或本地上游返回结果。

## 用户能力

- 查看个人博雅资料和修读统计。
- 分页浏览课程，支持状态、类别、校区等筛选。
- 查看课程详情和已选课程。
- 选择课程、退选课程。
- 对支持签到的博雅课程执行签到或签退。

## 技术路径

- UI 分为首页、课程列表、课程详情、已选课程和统计页。
- `BykcApiBackend` 定义所有业务动作，relay 模式通过 Ktor 路由访问服务端。
- `LocalBykcApiBackend` 实现直连/WebVPN 模式，包含上游登录、加密请求、分页、签到配置和动作解析。
- `BykcService` 在服务端缓存上游客户端上下文，并在应用停止或定期清理时释放缓存。

## 接口

- `GET /api/v1/bykc/profile`
- `GET /api/v1/bykc/courses`
- `GET /api/v1/bykc/courses/{courseId}`
- `GET /api/v1/bykc/courses/chosen`
- `GET /api/v1/bykc/statistics`
- `POST /api/v1/bykc/courses/{courseId}/select`
- `DELETE /api/v1/bykc/courses/{courseId}/select`
- `POST /api/v1/bykc/courses/{courseId}/sign`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/bykc/BykcHomeScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/bykc/BykcCoursesScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/bykc/BykcViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/BykcApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalBykcApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Bykc.kt`
- `server/src/main/kotlin/cn/edu/ubaa/bykc/BykcRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/bykc/BykcService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/bykc/BykcCrypto.kt`
- `composeApp/src/commonTest/kotlin/cn/edu/ubaa/ui/screens/bykc/BykcCourseFiltersTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/bykc/BykcRoutesTest.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/LocalBykcApiBackendTest.kt`
