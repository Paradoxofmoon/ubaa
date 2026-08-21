# 图书馆座位

图书馆座位模块覆盖楼馆查询、分区查询、座位查询、座位预约、预约列表和取消预约。预约和取消预约属于写操作，审查或排障时不要用真实账号执行提交，除非用户明确授权。

## 用户能力

- 按日期查看可用楼馆和分区。
- 查看分区详情、座位列表和可预约时间段。
- 选择座位、日期、开始时间和结束时间后提交预约。
- 查看个人预约记录。
- 取消已有座位预约。

## 技术路径

- `RegularFeaturesScreen` 提供“图书馆座位”普通功能入口。
- `MainAppScreen` 注册 `LIBBOOK_HOME`、`LIBBOOK_RESERVE`、`LIBBOOK_BOOKINGS` 三个页面。
- `LibBookViewModel` 管理楼馆、分区、座位、预约和预约记录状态。
- `LibBookApiBackend` 是 shared 层契约；`RelayLibBookApiBackend` 访问 server；`LocalLibBookApiBackend` 在本地连接模式下访问图书馆上游。
- `LibBookService` 和 `LibBookClient` 处理服务器中转、CAS 登录、上游 token、超时和错误映射。

## 接口

- `GET /api/v1/libbook/libraries`
- `GET /api/v1/libbook/areas`
- `GET /api/v1/libbook/areas/{areaId}`
- `GET /api/v1/libbook/areas/{areaId}/seats`
- `POST /api/v1/libbook/bookings`
- `GET /api/v1/libbook/reservations`
- `POST /api/v1/libbook/bookings/{bookingId}/cancel`

## 维护重点

- `POST /api/v1/libbook/bookings` 和 `POST /api/v1/libbook/bookings/{bookingId}/cancel` 会改变真实预约状态，只能在明确授权后做 live probe。
- 本地实现和服务端实现都需要保留 `libbook_auth_failed`、`libbook_not_found`、`libbook_seat_unavailable` 等稳定错误语义。
- 图书馆上游返回结构变化时，优先补充 `LibBookRoutesTest`、`LibBookServiceTest` 或本地 backend 测试，再改 parser。

## 来源文件

- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/libbook/LibBookHomeScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/libbook/LibBookReserveScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/libbook/LibBookBookingsScreen.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/libbook/LibBookViewModel.kt`
- `composeApp/src/commonMain/kotlin/cn/edu/ubaa/ui/screens/menu/RegularFeaturesScreen.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/feature/LibBookApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/api/local/LocalLibBookApi.kt`
- `shared/src/commonMain/kotlin/cn/edu/ubaa/model/dto/LibBook.kt`
- `server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookRoutes.kt`
- `server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookService.kt`
- `server/src/main/kotlin/cn/edu/ubaa/libbook/LibBookClient.kt`
