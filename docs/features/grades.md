# 成绩查询

成绩模块读取北航成绩页面并转换为统一成绩 DTO。客户端支持选定学期查询，也支持全部学期加载；全部加载路径会并发发起多个学期请求，并优先保留用户当前选择学期的展示语义。

## 用户能力

- 查看课程名称、成绩、学分、绩点等信息。
- 按学期切换成绩列表。
- 一键加载全部学期成绩。
- 出错时通过 shared 的用户友好错误映射展示提示。

## 技术路径

- `GradeViewModel` 管理学期选择、全部成绩加载和 stale response 防护。
- `GradeApiBackend` 定义成绩查询契约；relay 模式走 `/api/v1/grade/list`。
- `LocalGradeApiBackend` 在直连/WebVPN 模式下直接访问成绩上游页面。
- `GradeService` 负责服务端解析教务成绩页面并返回 `BuaaScoreResponse`。

## 接口

- `GET /api/v1/grade/list?termCode=...`

## 注意事项

- 全部成绩加载属于性能敏感路径，修改时需要覆盖并发和当前学期优先展示。
- 旧客户端依赖版本检查和错误响应契约，服务端兼容性优先于客户端破坏性调整。

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/grade/GradeScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/grade/GradeViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/GradeApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalGradeApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Grade.kt`
- `server/src/main/kotlin/cn/edu/ubaa/grade/GradeRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/grade/GradeService.kt`
- `composeApp/src/commonTest/kotlin/cn/edu/ubaa/ui/GradeViewModelTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/grade/GradeRoutesTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/grade/GradeServiceTest.kt`
