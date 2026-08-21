# 课表与考试

课表模块提供学期、周次、周课表和今日课程；考试模块按学期读取考试安排。两个模块都依赖教务系统数据，并在服务器中转和本地连接模式之间保持同一 DTO 契约。

## 课表能力

- 查询学期列表和周次列表。
- 按学期与周次查看周课表。
- 查看今日课程摘要。
- 课程详情页面复用周课表中的课程数据。

## 考试能力

- 按学期查询考试安排。
- 展示考试名称、时间、地点、座位号等考试信息。
- 通过 ViewModel 的 `ensureLoaded()` 避免页面重复加载。

## 技术路径

- `ScheduleApiBackend` 和 `RelayScheduleApiBackend` 负责 relay 模式接口。
- `LocalScheduleApiBackend` 在本地连接模式下访问本科教务上游。
- 服务器的 `ScheduleService` 适配上游课表接口，`ScheduleRoutes` 暴露统一 HTTP API。
- 考试模块通过 `ExamService` 和 `ExamRoutes` 提供 `/api/v1/exam/list`。

## 接口

- `GET /api/v1/schedule/terms`
- `GET /api/v1/schedule/weeks?termCode=...`
- `GET /api/v1/schedule/week?termCode=...&week=...`
- `GET /api/v1/schedule/today`
- `GET /api/v1/exam/list?termCode=...`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/schedule/ScheduleScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/schedule/ScheduleViewModel.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/exam/ExamScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/exam/ExamViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/ScheduleApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalScheduleApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Schedule.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Exam.kt`
- `server/src/main/kotlin/cn/edu/ubaa/schedule/ScheduleRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/schedule/ScheduleService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/exam/ExamRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/exam/ExamService.kt`
- `server/src/test/kotlin/cn/edu/ubaa/schedule/ScheduleRoutesTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/exam/ExamRoutesTest.kt`
