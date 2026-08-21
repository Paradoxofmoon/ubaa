# 阳光打卡

阳光打卡模块读取体育活动概览和历史记录，并支持提交带活动类型、时间和图片的打卡记录。

## 用户能力

- 查看学期体育活动概览。
- 分页查看历史打卡记录。
- 选择活动项目、开始结束时间和图片后提交打卡。
- 表单校验会阻止缺少必要字段的提交。

## 技术路径

- `YgdkViewModel` 管理概览、记录分页、表单状态和提交状态。
- `YgdkApiBackend` 提供概览、记录和提交契约。
- `LocalYgdkApiBackend` 在本地连接模式下处理上游登录、分类、记录分页和上传字段转换。
- 服务端通过 `YgdkRoutes` 接收 multipart 或 JSON 数据，并由 `YgdkService` 转发上游。

## 接口

- `GET /api/v1/ygdk/overview`
- `GET /api/v1/ygdk/records`
- `POST /api/v1/ygdk/records`

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/ygdk/YgdkHomeScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/ygdk/YgdkClockinFormScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/ygdk/YgdkViewModel.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/YgdkApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalYgdkApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/Ygdk.kt`
- `server/src/main/kotlin/cn/edu/ubaa/ygdk/YgdkRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/ygdk/YgdkService.kt`
- `composeApp/src/commonTest/kotlin/cn/edu/ubaa/ui/YgdkViewModelTest.kt`
- `server/src/test/kotlin/cn/edu/ubaa/ygdk/YgdkRoutesTest.kt`
