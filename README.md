# UBAA · 北航校园客户端

一款面向北京航空航天大学的校园生活客户端（Kotlin Multiplatform + Compose Multiplatform），覆盖课表、成绩、校园卡、体育场馆预约、校园网、班车等常用校园服务。

本仓库为 [BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（MIT License）的衍生维护分支，按 MIT 协议开放源码，保留了最初作者的版权标注（见 [LICENSE](LICENSE)）。

---

## 功能特性

| 功能 | 说明 |
|---|---|
| 课表 / 考试 / 成绩 | 课表查询、考试安排、成绩查询 |
| 体育场馆预约（抢场） | 提前一天预选意向场地，开抢后**智能抢场**：服务器返回"已被定"时自动降级到下一意向，全程自动锁定 + 验证码人工点选 |
| 校园卡 | 余额查询、流水 |
| 校园网 | 流量查询、充值（zfw） |
| 电费 / 水费充值 | 复用 cc-pay 收银台唤起支付 |
| 其它 | 班车、希冀作业、评教、图书馆、公告等 |

> 所有上游数据均直接访问北航校内系统（`direct` / `webvpn` 两种连接模式），不经过任何第三方中转服务器。

---

## 连接模式

| 模式 | 说明 |
|---|---|
| 直连模式 | 在校园网/校内 VPN 环境下直接访问校内系统 |
| WebVPN 模式 | 通过北航 WebVPN 网关访问校内系统 |

登录态按连接模式隔离存储，切换模式会清空对应会话。

---

## 环境要求

- JDK 21
- Android SDK（compileSdk 36）
- Gradle 9.x（仓库自带 `gradlew`）

国内网络下，Gradle 构建会自动使用阿里云镜像加速（CI 环境自动切回官方源）。

---

## 构建

```bash
# Android Debug
./gradlew :androidApp:assembleDebug

# Android Release（签名信息见下方说明）
./gradlew :androidApp:assembleRelease

# 运行共享模块单元测试
./gradlew :shared:jvmTest
```

### 发布签名

Release 签名在 `local.properties` 中配置（测试用 debug 密钥库即可覆盖安装；正式上架请替换为正式 keystore）：

```properties
SIGNING_KEY=<keystore 路径>
SIGNING_STORE_PASSWORD=<密码>
SIGNING_KEY_ALIAS=<别名>
SIGNING_KEY_PASSWORD=<密码>
```

### 可选：自建服务端能力

「检查更新」与「公告」功能默认关闭（不依赖任何第三方服务器）。如需启用，构建时设置 `API_ENDPOINT` 环境变量指向自建服务端即可：

```bash
API_ENDPOINT=https://your-server.example.com ./gradlew :androidApp:assembleDebug
```

---

## 项目结构

```
ubaa/
├── androidApp/     # Android 应用壳：MainActivity、桌面小组件
├── composeApp/     # KMP 核心 UI（Compose Multiplatform，含 Android/iOS/桌面/Web 目标）
├── shared/         # 共享业务逻辑：登录/会话/各校园服务 API
├── iosApp/         # iOS 壳工程
└── buildSrc/       # 构建辅助
```

---

## 致谢与版权

- **最初作者**：[BUAASubnet/UBAA](https://github.com/BUAASubnet/UBAA)（MIT License）——本仓库基于其源码二次开发。
- **维护者**：[Paradoxofmoon](https://github.com/Paradoxofmoon)
- 本项目遵循 [MIT License](LICENSE)。
