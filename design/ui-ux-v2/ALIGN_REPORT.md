# TicketKeep UI/UX v2 — 全量对齐报告

日期：2026-09-19（Asia/Shanghai）  
输入：`/workspace/v2-gap-src/`（DESIGN_SPEC / tokens / index.html / GORK + 待改 `.kt`）  
输出：`/workspace/v2-full-out/app/src/main/java/com/ticketkeep/app/...`  
本环境：**未跑 Gradle / 无模拟器**；视觉与交互以源码对照规范验收。

## 1. 已完成页面 / 文件

| 包路径 | 对齐要点 |
|---|---|
| `ui/navigation/HomeBottomBar.kt` | **关键修复**：去掉圆形 FAB，改为宽胶囊主按钮「存一张票证」（高 ≥48dp / 约 49dp，圆角 16，minWidth 113），夹在「票证」「我的」之间；保留 `onAdd`；仅 list/settings 显示 |
| `ui/screens/list/ListScreen.kt` | §4.1 IA：品牌「票证记」+ slogan → 临期摘要（min 156dp）→ 搜索 → 芯片 全部/即将到期/未过期/未设保修 → 列表；空态/搜索空文案；行缺省「未命名商家」「—」「未设购买日」；缩略图 44×54；搜索/筛选时收起摘要；无列表 FAB；添加面板标题「存一张票证」；通知/Pro 横幅互斥（未改 prefs key） |
| `ui/screens/list/ListViewModel.kt` | 全量 `soonCount`；SOON→NOT_EXPIRED+到期日[today,today+30]（沿用） |
| `ui/screens/detail/DetailScreen.kt` | 摘要置顶；无假图；主 CTA 送修/编辑；更多含编辑/删除；删除确认「保留票证 / 确认删除」 |
| `ui/screens/edit/EditScreen.kt` | 三组表单；「不设置保修」；dirty 返回「继续编辑 / 放弃修改」；IME 安全底栏 |
| `ui/screens/settings/SettingsScreen.kt` | 「本地票证册」n/10 或 Pro 不限（无进度条）；分组「数据与备份 / 保修提醒 / 关于」；Tab 无 up-nav；China 备份不购买循环 |
| `ui/screens/backup/CloudBackupScreen.kt` | Pro 门禁保留；China 非 Pro 说明+返回；Base URL 仅 Debug；圆角/页边距 token 化 |
| `ui/screens/paywall/PaywallScreen.kt` | Billing 真价；无硬编码 ¥/商品 ID 用户文案；China 无购买 |
| `util/MoneyFormats.kt` | `formatYuan(null)` →「—」 |
| `ui/theme/{Color,Theme,Type}.kt` | tokens 已映射；`dynamicColor` 默认 **false**；shapes 7/12/18/22/26 |
| `ui/components/PaperCard.kt` | card 22 / ticket 18 |
| `MainActivity.kt` | 底栏 + `addTrigger`；settings `showUpNavigation=false` |

## 2. 本轮相对 gap-src 的实质改动

1. **HomeBottomBar**：圆形 FAB → 宽主按钮「存一张票证」（最高优先级缺口）。  
2. **ListScreen**：摘要 min 156dp；添加 sheet 标题；空态 CTA 文案「拍照」「相册」。  
3. **DetailScreen**：删除确认主次对齐原型（保留 / 确认删除）。  
4. **EditScreen**：芯片「不设置保修」；放弃对话框主次对齐原型。  
5. **SettingsScreen**：分组标题「通知」→「保修提醒」。  
6. **CloudBackupScreen / MoneyFormats / MainActivity**：token 与 KDoc 微调。

## 3. 诚实剩余缺口（未在本包重写或环境限制）

| 项 | 说明 |
|---|---|
| 高级筛选「草稿取消不应用」 | 仍即时写入 filter（与改版前一致）；规范理想态可二期 |
| `EditViewModel` / `SettingsViewModel` / `SectionHeader` / `SettingsRow` / `WarrantyStatusChip` / `WarrantyStatus` | **不在 gap-src**；依赖工程内既有（或上一轮 `ui-ux-v2-out`）文件，合并时勿漏 |
| 列表初始加载 vs「已删除」区分 | Detail 仍以 `ticket == null` 显示不存在；无独立 Loading 态（需 ViewModel 配合） |
| OCR 完成提示「已识别部分信息，请核对后保存」 | 依赖 EditViewModel 状态字段；本包仅 UI 侧识别中态 |
| 筛选跨午夜自动刷新 SOON 区间 | ViewModel 在筛选时用 `LocalDate.now()`；未加日期变更监听 |
| 真机 / 模拟器截图 | 本 box 无 Android SDK，**未做视觉截图验收** |
| TalkBack / 320dp / 200% 字号 | 未跑设备验证 |
| Widget 视觉 | 按 GORK：主流程后轻量匹配，本轮未改 |

## 4. 合并与验收步骤（给父代理 / 本地）

1. 将 `v2-full-out/app/src/main/java/com/ticketkeep/app/**` 覆盖到工程同名包；确认上一轮组件与 ViewModel 仍在。  
2. 编译：
   ```powershell
   .\gradlew.bat :app:assembleChinaDebug :app:assemblePlayDebug
   .\gradlew.bat :app:testChinaDebugUnitTest :app:testPlayDebugUnitTest
   ```
3. 手测清单：
   - [ ] 底栏中间为宽按钮「存一张票证」，非圆 FAB；点开拍照/相册/手填；列表无第二 FAB  
   - [ ] 首页 IA 顺序与 slogan；临期有/无文案；搜索空「没有找到这张票证」；首用空「给重要的票证，一个家」  
   - [ ] 行：空商家/空金额/空购买日；缩略图 44×54  
   - [ ] 详情摘要在上、无假图；删除确认文案；China 主按钮为「编辑票证」  
   - [ ] 编辑「不设置保修」保存后字段清空；非法金额字段错误；键盘下保存可达  
   - [ ] 我的：容量 n/10 或 Pro 不限；「保修提醒」分组；China 云备份说明不循环付费墙  
   - [ ] Paywall：价格来自 Billing；无商品 ID；China 无购买按钮  
   - [ ] 深浅色；通知横幅与 Pro 横幅不同时出现  

## 5. 刻意未改

ChannelConfig、Billing 商品 ID、Repository/DAO/加密、通知 banner prefs 键、FREE_TICKET_LIMIT=10、字段名 merchantName/note/amountCents。
