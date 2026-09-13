# agent.md — 票证记（ticket-keep）给 AI 助手的工程指南

> 给 coding agent 看。改代码前先读完本文件。
> 中文名：票证记 · 包名：`com.ticketkeep.app` · 仓库：https://github.com/Trans88/ticket-keep

## 产品定位（锁死，勿扩）

本地优先的 **收据 / 保修管家** Android App（MVP）。

**做：**
- 拍照或 Photo Picker 选图 → 端侧 ML Kit OCR → 手改商家 / 日期 / 金额 / 保修
- Room 本地持久化 + 应用私有目录存图
- 保修到期本地通知（WorkManager）
- 免费最多 **10** 条 + Pro 付费墙 **UI 占位**（本地 DataStore 开关，无真实 Billing）

**不做：**
- 云同步、账号体系、家庭共享、保险理赔、ToB 报销
- 不要擅自加网络请求、账号、后端、广告、分析 SDK

## 技术栈

| 项 | 版本 / 选型 |
|----|-------------|
| 语言 / UI | Kotlin, Jetpack Compose, Material 3 |
| 构建 | AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21, JDK 17 |
| SDK | `minSdk 26`, `targetSdk` / `compileSdk` 35 |
| DI | Hilt + KSP |
| 存储 | Room 2.6.1, DataStore Preferences |
| OCR | ML Kit `text-recognition-chinese` |
| 提醒 | WorkManager + `@HiltWorker` + BootReceiver |
| 图片 | Coil；FileProvider 拍照缓存 |

版本目录：`gradle/libs.versions.toml`。

## 目录结构

```
app/src/main/java/com/ticketkeep/app/
  MainActivity.kt          # 导航壳、通知深链（singleTop + onNewIntent）
  TicketKeepApp.kt         # @HiltAndroidApp，提供 HiltWorkerFactory
  data/
    local/                 # Room DB/Dao、ProPreferences
    model/Ticket.kt
    repository/TicketRepository.kt   # FREE_TICKET_LIMIT = 10
  di/AppModule.kt
  ocr/                     # MlKitOcrHelper（含采样）、TicketOcrParser
  notification/            # Scheduler / Worker / BootReceiver
  ui/screens/{list,edit,detail,paywall}/
  util/                    # ImageStorage、DateFormats、MoneyFormats
```

资源注意：
- 备份规则：`res/xml/backup_rules.xml`、`data_extraction_rules.xml`（**必须排除** Room DB 与 `ticket_images`）
- FileProvider：`res/xml/file_paths.xml`（cache `images/`、files `ticket_images/`）

## 架构约定

1. **UI → ViewModel → Repository → Dao / DataStore / Scheduler**；不要在 Composable 里直接碰 Room。
2. 新增依赖优先写进 `libs.versions.toml`，再在 `app/build.gradle.kts` 引用。
3. 改动前用清晰技术语言说明方案；保持 MVP 范围。
4. 中文 UI 文案；用户沟通默认中文。
5. 本地工程路径（优先直接改这里）：`E:\project\ticket-keep-mvp\ticket-keep`

## 关键行为备忘

### 免费额度
- `TicketRepository.FREE_TICKET_LIMIT = 10`
- 列表添加与 `EditViewModel.save` 都会校验 `canAddTicket()`；超限进 Paywall。

### OCR
- `MlKitOcrHelper` 解码时按最长边约 **2048** `inSampleSize` 采样，降低 OOM。
- 解析失败字段可为 null，**表单始终可手改**；不要做成「识别失败就阻塞保存」。

### 通知 / 深链
- `MainActivity`：`launchMode=singleTop`，`onNewIntent` + `EXTRA_TICKET_ID` 导航到详情。
- `WarrantyReminderWorker`：在 `[targetDay, warrantyEnd]` 内都可发送（允许 Doze 延迟）；**到期后**不再发。
- Android 13+：列表页有通知权限横幅（再申请 / 跳系统设置）。
- WorkManager 默认 Initializer 已移除；`TicketKeepApp` 实现 `Configuration.Provider`。

### 隐私
- 无 `INTERNET` 权限（请保持）。
- 备份 / 设备迁移：**不得**把 `ticket_keep.db*` 和 `ticket_images/` 打进云备份。
- 图片只存应用私有目录；删除票证时删对应文件（见 `DetailViewModel`）。

## 代码与测试要求（强制）

### 类注释
- **每个类**（含 `data class`、`object`、重要的文件级顶层声明若承担独立职责）都必须有 **KDoc 类注释**：说明职责、边界、关键协作对象；不要写废话注释。
- 新增/改动公共 API、OCR 解析规则、提醒调度、Repository 等「为什么这么做」的逻辑，在方法上补简洁中文注释。

### 单元测试（AI / 开发者自行跑通）
- **主要功能必须有单元测试**，并且改完后要 **自己在本机执行并确认通过**，不能只写测试不跑。
- 优先 JVM 单测（不依赖真机）：如 `TicketOcrParser`、金额/日期工具、Repository 规则等。
- 建议命令（在工程根目录）：
  ```bash
  ./gradlew.bat :app:testDebugUnitTest
  ```
  或只跑某一类：
  ```bash
  ./gradlew.bat :app:testDebugUnitTest --tests "com.ticketkeep.app.ocr.TicketOcrParserTest"
  ```
- 向用户回报时写明：跑了哪些测试、通过/失败、失败原因；失败则先修到绿再交付。
- UI / ML Kit 真机路径可用手工验证补充，但 **解析与业务规则不能只靠手工**。


## 构建与验证

```bash
# Android Studio：Open 本仓库根目录（含 settings.gradle.kts）
./gradlew :app:assembleDebug
```

建议手测：
1. Sync 成功，Debug 安装 API 26+。
2. 选图 / 拍照 → OCR → 手改 → 保存 → 列表 / 搜索 / 详情 / 删除。
3. 拒绝通知权限 → 见横幅 → 再申请或进设置。
4. 保修提醒：可临时改 Scheduler 延迟验证；点通知应打开对应详情（含 App 已在前台）。
5. 第 11 条应进 Pro 墙；「模拟开通 Pro」仅本地开关。

## 改动时请勿

- 引入云同步 / 登录 / 家庭码 / 真实 Billing（除非任务明确要求）
- 放宽备份把收据图或数据库备份到云
- 为「省事」恢复过严的 Worker 日期窗口（`abs(today-target)>1` 那种会静默丢提醒）
- 在未说明方案的情况下大重构包结构或换架构

## 提交建议

- 小步提交；中文或英文 commit message 均可，说明 **为什么**。
- 若只改助手文档：`docs: 更新 agent.md`

## 近期已落地的修复（参考）

- P0：备份排除 DB + `ticket_images`
- P1：通知深链、`Worker` 提醒窗口放宽、通知权限引导、OCR 采样

---

有冲突或要拍板产品范围时，交给用户或幕僚长，不要擅自扩需求。

## OCR 说明（小票 + 保修单）

- `TicketOcrParser` 同时解析收银小票与保修/报修信息表。
- 保修单标签：购买日期、保修期限（年/月→月数）、保修截止日期、故障描述、商家/门店/服务商。
- 没有金额时保持空（保修单常见）；编辑页上方展示「识别原文」卡片便于核对。
- 原文为空或几乎未解析出字段时，会给出明确提示，避免静默只填「今天」。

## 注释约定
- 每个 Kotlin class/interface/object 必须有中文类头 KDoc；新建或改动缺失时一并补上。
