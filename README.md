# 票证记（ticket-keep）

本地优先的收据 / 保修管家 Android App（MVP）。

- **中文名**：票证记  
- **包名**：`com.ticketkeep.app`  
- **定位**：拍照或选图 → 端侧 OCR → 手改 → 本地保存 → 保修到期提醒  
- **不做**：云同步、账号体系、家庭共享、保险理赔、ToB 报销

仓库目标地址（由你自行推送）：https://github.com/Trans88/ticket-keep

---

## 如何用 Android Studio 打开 / 构建

1. 安装 [Android Studio](https://developer.android.com/studio)（建议 Ladybug / 最新稳定版），并配置 JDK 17、Android SDK 35。
2. **File → Open**，选择本项目根目录（含 `settings.gradle.kts` 的文件夹）。
3. 等待 Gradle Sync 完成（首次会下载依赖与 ML Kit 模型相关库）。
4. 连接真机或启动模拟器（API 26+），点击 Run。
5. 或命令行（需本机已配置 `JAVA_HOME` 与 Android SDK）：

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> 若缺少 `local.properties`，Android Studio 打开时会自动生成；也可手动写入：
> `sdk.dir=/你的/Android/Sdk路径`

---

## MVP 功能范围

| 功能 | 状态 |
|------|------|
| 拍照 / Photo Picker 选图添加 | ✅ |
| ML Kit 中文+Latin OCR，启发式解析商家/日期/金额 | ✅ |
| 表单始终可手改；保修到期日或「购买日+月数」 | ✅ |
| Room 本地持久化 | ✅ |
| 列表、搜索、详情、删除 | ✅ |
| 到期前本地通知（默认 7 天 / 1 天 / 当天） | ✅ WorkManager |
| 免费上限 10 条 + Pro 付费墙 UI 占位 | ✅（无真实 Billing） |
| 云同步 / 账号 / 家庭码 | ❌ 不做 |

---

## 如何验证通知

本项目使用 **WorkManager** 调度保修提醒（见下方架构说明）。

### 真机 / 模拟器验证步骤

1. 安装 Debug 包，首次启动时允许「通知」权限（Android 13+）。
2. 新建一条票证，将**保修到期日**设为「今天 + 7 天」（或改代码中的提醒日做调试）。
3. 默认提醒时刻为到期前 N 天的 **本地时间 09:00**。  
   - 想立刻验证：可临时把 `WarrantyReminderScheduler` 里的 `LocalTime.of(9, 0)` 改为当前时间后 1～2 分钟，或把 `daysBefore` 对应的 `fireAt` 改成 `now.plusMinutes(1)`。
4. 等待 WorkManager 触发后，应收到「保修还有 N 天到期」通知；点击可打开对应详情。
5. 删除该票证后，对应 Work 会被取消。
6. 重启手机后，`BootReceiver` 会重新 `rescheduleAll`（双保险）。

### 调试技巧

- Android Studio → **App Inspection → Background Task Inspector** 可查看已入队的 Work。
- `adb shell dumpsys jobscheduler` / `adb shell dumpsys activity service androidx.work` 也可辅助排查。

---

## 架构简述

```
app/
  data/          Room Entity/Dao/Database、DataStore(Pro 开关)、Repository
  di/            Hilt Module
  ocr/           ML Kit 中文识别 + 启发式字段解析
  notification/  WorkManager Worker + Scheduler + BootReceiver
  ui/            Compose Material 3：列表 / 编辑 / 详情 / Pro 墙
```

- **UI**：Jetpack Compose + Navigation + Material 3  
- **DI**：Hilt（含 `@HiltWorker`）  
- **存储**：Room（票证）+ DataStore（是否 Pro）+ 应用私有目录存图片  
- **OCR**：`text-recognition-chinese`（覆盖中文与常见 Latin）  
- **提醒**：WorkManager 一次性任务；选它而非 AlarmManager 的原因：重启恢复更省心、与 Hilt 集成简单、MVP 对「整点精确闹钟」无强需求。Boot 时再调度一次作为补充。

### 免费 / Pro 策略（MVP）

- 免费最多 **10** 条（`TicketRepository.FREE_TICKET_LIMIT`）。
- 超出时进入 Pro 付费墙；墙内「模拟开通 Pro」仅写本地 DataStore，**未接入 Google Play Billing**。

---

## 后续步骤建议

1. 接入 Play Billing（订阅 / 买断）替换 Pro 占位。  
2. 优化 OCR 解析（商家词典、金额行置信度、多币种）。  
3. 保修提醒可配置（提前天数、静默时段）。  
4. 导出 / 备份（本地 ZIP），仍保持无强制账号。  
5. 仪表盘：即将到期、本月支出等。  
6. 发布前开启 R8、补齐单元测试与截图。

---

## 推送到 GitHub

本工程为从零生成，**不要** `git clone` 覆盖。在本目录初始化后推送即可：

```bash
cd ticket-keep
git init
git add .
git commit -m "feat: ticket-keep MVP (local OCR + warranty reminders)"
git branch -M main
git remote add origin https://github.com/Trans88/ticket-keep.git
git push -u origin main
```

若远程已有 README，先 `git pull --rebase origin main` 再 push，或按需使用 `--force`（确认无重要历史后再强推）。

---

## 技术版本（Version Catalog）

见 `gradle/libs.versions.toml`：AGP 8.7.3、Kotlin 2.0.21、Compose BOM 2024.12.01、Room 2.6.1、Hilt 2.53.1、WorkManager 2.10.0、ML Kit Text Recognition Chinese 16.0.1。`minSdk 26`，`targetSdk / compileSdk 35`。
