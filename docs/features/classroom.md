# 空教室查询

空教室模块按校区、日期和节次查询可用教室。服务端和本地实现都以 `ClassroomQueryResponse` 为统一返回结构。

## 用户能力

- 选择校区、日期和节次组合。
- 查看按楼宇或教室分组的空闲结果。
- 查询失败时显示可读错误信息。

## 技术路径

- `ClassroomQueryScreen` 和 `ClassroomViewModel` 管理查询表单与结果状态。
- `ClassroomApiBackend` 定义查询契约。
- `LocalClassroomApiBackend` 在本地连接模式下访问上游空教室接口。
- `ClassroomClient` 和 `ClassroomRoutes` 提供服务器中转能力。

## 接口

- `GET /api/v1/classroom/query`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/classroom/ClassroomQueryScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/classroom/ClassroomViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/ClassroomApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalClassroomApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Classroom.kt`
- `server/src/main/kotlin/cn/edu/ubaa/classroom/ClassroomRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/classroom/ClassroomClient.kt`
- `composeApp/src/commonTest/kotlin/cn/edu/ubaa/ui/ClassroomViewModelTest.kt`
- `shared/src/commonTest/kotlin/cn/edu/ubaa/api/LocalClassroomApiBackendTest.kt`
