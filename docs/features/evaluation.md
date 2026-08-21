# 自动评教

自动评教模块读取待评课程并批量提交默认评价结果。它属于高风险写操作，维护时必须确认用户入口、默认选择和提交结果展示都符合预期。

## 用户能力

- 读取当前待评课程列表。
- 查看评教进度。
- 切换课程选择状态。
- 一键提交已选课程的评教。
- 查看每门课程提交结果。

## 技术路径

- `EvaluationViewModel` 维护课程选择、进度和批量提交结果。
- `EvaluationServiceBackend` 定义列表与提交契约。
- `LocalEvaluationServiceBackend` 在本地连接模式下完成上游待办、问卷、题目和提交参数转换。
- 服务端 `EvaluationService` 和 `EvaluationClient` 复用相同上游流程，`EvaluationRoutes` 暴露 relay API。

## 接口

- `GET /api/v1/evaluation/list`
- `POST /api/v1/evaluation/submit`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/evaluation/EvaluationScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/evaluation/EvaluationViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/EvaluationService.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalEvaluationService.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/evaluation/EvaluationModel.kt`
- `server/src/main/kotlin/cn/edu/ubaa/evaluation/EvaluationRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/evaluation/EvaluationService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/evaluation/EvaluationClient.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/LocalEvaluationServiceBackendTest.kt`
