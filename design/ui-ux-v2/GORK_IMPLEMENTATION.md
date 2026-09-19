# 交给 gork bot：实现票证记 UI / UX v2

## 最新补充：Pro 页 v2.1（优先于旧版 Pro 示意）

只调整 Pro 页展示，不修改权益、商品或计费策略。重新打开 `index.html?screen=pro`，阅读 `DESIGN_SPEC.md` 的 4.6 节：

- 改为紧凑价值区 + 普通版 / Pro 逐项对比，优先展示“10 张 / 不限数量”，再比较 PDF、CSV、加密备份；基础免费能力另外说明。
- 对比表逐行布局，自适应行高，不能用三列各自固定高度的纵向列表模拟；当前 `PaywallScreen.kt` 若已存在 `FreeVsProCompareCard`，在现有改动上增量调整，不能整体覆盖。重点验收换行、大字号和行对齐。
- 用真实 `isPro` 区分普通和已开通状态；后者显示权益确认与“继续使用”，不再显示开通按钮。原型的身份下拉框绝不放进正式应用。
- 用 `Scaffold` 底部区域承载购买状态和操作，正文独立滚动。尊重导航栏 / 手势安全区；小屏大字号时保证内容和操作均可达。
- China 不展示可购买状态，对当前未开放的导出 / 导入标注“未开放”，而非承诺解锁。云备份仍需有效 Pro 与账号。
- 保留价格加载、失败重试、恢复购买和购买结果处理。原型演示获取失败，不允许将该失败文案硬编码为所有运行状态。

验收：普通 / Pro × China / Play × 深浅色；320 dp 窄屏与 1.5–2.0 字体倍率下不得竖排挤压、错行或遮挡按钮。已有用户数据与购买状态不变。

## 可以直接执行的任务

请在 `E:/project/ticket-keep-mvp/ticket-keep` 中，把现有 Android Jetpack Compose 界面实现为本目录 `index.html` 所展示的「好好收着」设计。先完整阅读 `DESIGN_SPEC.md` 和 `tokens.json`，再修改代码。HTML 是视觉及交互参考，不要用 WebView 包装 HTML 交差，不要只换主题颜色。

优先级：真实业务行为与数据完整性 > 本规范交互与可访问性 > 原型视觉尺寸。原型中的示例状态、手机边框、固定日期、演示数据、设计侧栏不进入 App。表单、网络、权限和支付的全部状态按规范补全。原型没有绘制完整的次级面板，不代表这些功能可被删除。

这是 UI / UX 实现任务：不要接新支付、改 API / 数据库 / 加密算法 / 签名配置 / 服务器部署，也不要更换现有用户数据。China 当前没有购买和导出，设计不得把这些功能悄悄打开。

## 开工先核对

读取仓库适用的 AGENTS.md（若有），检查 git diff。设计产生时，以下文件已有用户未提交修改，后续可能更多，必须在其上增量工作，不能覆盖还原：

- `app/src/main/java/com/ticketkeep/app/ui/screens/backup/CloudBackupScreen.kt`
- `app/src/main/java/com/ticketkeep/app/ui/screens/backup/CloudBackupViewModel.kt`
- `app/src/main/java/com/ticketkeep/app/ui/screens/edit/EditScreen.kt`
- `app/src/main/java/com/ticketkeep/app/ui/screens/list/ListScreen.kt`
- `app/src/main/java/com/ticketkeep/app/widget/ExpiringTicketsSelector.kt`
- `app/src/main/java/com/ticketkeep/app/widget/ExpiringTicketsWidget.kt`
- `app/src/test/java/com/ticketkeep/app/widget/ExpiringTicketsSelectorTest.kt`

README 陈旧：源码已有 Billing、云备份、CSV 导入导出、PDF、送修材料与桌面组件。不要按 README 的“未实现”描述删除或重做它们。

## 当前代码与设计映射

下表路径均相对 `app/src/main/java/com/ticketkeep/app/`。

| 当前位置 | 实现任务 |
|---|---|
| `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` | 按 tokens 映射 MaterialTheme；保持浅 / 深色，关闭自动动态色作为默认 |
| `ui/components/PaperCard.kt` | 统一卡片边框 / 圆角 / 间距，不复制每屏样式 |
| `ui/components/WarrantyStatusChip.kt`, `ui/theme/WarrantyStatus.kt` | 状态颜色和文字一致，保留 today / 30-day 边界 |
| `ui/navigation/HomeBottomBar.kt` | 两侧票证 / 我的，中间添加动作；不得重复保留列表 FAB |
| `MainActivity.kt`, `ui/navigation/Routes.kt` | 传递添加回调，保留实际导航栈 / 深链 / 小组件入口 |
| `ui/screens/list/ListScreen.kt` | 临期摘要、轻搜索、筛选面板、记录行、空态 |
| `ui/screens/list/ListViewModel.kt`, `TicketListFilter.kt` | 全量摘要统计、快捷临期组合筛选、必要加载状态；不破坏现有过滤 |
| `ui/screens/detail/DetailScreen.kt` | 票证摘要置顶、原图下移、底部主动作、更多菜单 |
| `ui/screens/edit/EditScreen.kt`, `EditViewModel.kt` | 分组表单、保修模式、错误 / dirty / 保存中状态 |
| `ui/screens/settings/SettingsScreen.kt` | 本地票证册、容量、分组列表；权限排查进入次级面板 |
| `ui/screens/backup/CloudBackupScreen.kt`, `CloudBackupViewModel.kt` | 权益 / 未登录 / 已登录 / 操作中状态；口令逐步展开 |
| `ui/screens/paywall/PaywallScreen.kt`, `PaywallViewModel.kt` | Pro 价值呈现、真实价格、清晰错误、恢复购买 |
| `ui/screens/privacy/PrivacyPolicyScreen.kt` | 仅统一排版，不删改事实性隐私声明和备案信息 |
| `ui/components/ZoomableImageViewer.kt` | 继续复用长图与缩放查看功能 |
| `widget/ExpiringTicketsWidget.kt` | 完成手机主要页面后再轻量匹配视觉；不覆盖现有选择逻辑 |

## 实现顺序与每阶段结果

### A. 主题与公共组件

落地 tokens 的浅 / 深色和 typography，完成卡片、按钮、字段、状态徽标、设置行。建立常规 / 320 dp / 200% 字号的 Compose Preview。不要把所有圆角设成同一个值；不能用不同深浅的默认 Material3 tonal elevation 代替设计颜色。

### B. 首页与底栏

两个 Tab 加添加按钮，复用现有拍照 / 图片选择 / 免费额度检查。将已有新增入口收敛为同一动作，主页面不叠加两个新增按钮。

首页摘要读取全量数据（新增 DAO 聚合或独立全量 flow 均可，按项目结构选简单方案），搜索结果数独立。临期统计范围 `[today, today+30]`、今天包含在内。点击“即将到期”原子更新整套条件，日期变更后正确刷新；不要因跨午夜、旋转或重新进入保留过时日期。

高级日期筛选用面板内草稿，取消不应用；保留已有购买日 / 到期日字段、起止日与清空能力。状态保存和列表回到原滚动位置是验收点。

### C. 详情与编辑

重排信息层次，复用现有导出与图片组件。表单正常 / 识别中 / 失败 / 金额错误 / 保存中 / 保存失败 / dirty 返回要完整。现有 `EditViewModel.save()` 未显式处理持久化异常，改版实施时为 UI 所需增加 try/catch/finally，避免一直转圈；不要重写 Repository。

补齐“不设置保修”的 UI 状态和字段清空映射。保修月份默认值仍沿用项目实际行为；新增交互不能在用户明确清空后自动恢复 12 月。日期转换继续使用已有 DateFormats / DateBounds，不把网页示例 JS 日期算法照搬到 Kotlin。

### D. 我的、云备份、Pro

设置按数据 / 通知 / 关于分组。云备份保留多层 Pro 检查及恢复确认。备份入口是否可进入取决于有效权益，不可简单等同于是否 Play 渠道。非 Pro + China 展示不可购买说明，不能陷入循环跳转。

Pro 的价格及周期由 Billing 商品驱动。将 developer 商品 ID 错误移到调试日志，不展示给普通用户。购买、恢复、取消、pending 和错误状态都接现有结果；不修改权益授予方式。

### E. 发布前对照

按规范的六屏和状态表逐项对照，提供实际模拟器 / 真机截图；截图需注明尺寸、字体倍率、渠道、深浅色、示例状态。若无法运行 Android，明确说明视觉验证未完成，不把网页截图当成 App 验收。

## 必须保留的业务语义

1. 实体 `Ticket` 没有商品名 / 标签 / 文件夹；主标题是 `merchantName`，说明使用 `note`，金额是 `amountCents`。
2. 缺失日期为 null，不能自动填今天；缺失金额不是零；实际可选字段不擅自改必填。
3. `FREE_TICKET_LIMIT=10`，现有票证可编辑；不擅自变更额度或解锁 Pro。
4. `ChannelConfig.showProPurchase` 和 `showProExport` 决定入口；当前 China 均 false。
5. 云备份是手动上传；恢复追加，不清空、不覆盖、不自动去重。
6. 通知仍由现有调度机制处理，不承诺整点必达，不新增精确闹钟权限。
7. 本地票证不强制登录；账号密码与备份口令分别处理。口令不进日志、分析埋点或 SavedStateHandle。
8. 图片 URI 持久化、Photo Picker、相机权限、系统分享、FileProvider 均沿用当前工程。
9. 任何尚未接入的渠道购买能力不能通过原型文案变成正式功能。

## 验收清单

- [ ] 至少 360×800 与 412×915 dp 浅色、深色；320 dp 和 200% 字号检查无横向溢出与遮挡。
- [ ] 首页主内容可滚动、底栏固定；键盘弹出时编辑保存按钮可达。
- [ ] 无图、空商家、空金额、长商家、超长备注、大金额均不会挤坏布局。
- [ ] 保修 31 / 30 / 1 / 0 / -1 天、null 状态与筛选一致；“未过期”包含临期。
- [ ] 搜索只承诺商家和备注；详情返回保留搜索 / 筛选 / 滚动。
- [ ] OCR 中防重复保存，失败可手填；重识别不静默覆盖用户修改。
- [ ] 保存失败保留表单；金额非法有反馈；购买日缺失不造假；日期按本地自然日处理。
- [ ] 免费第 10 / 11 张新增门禁正确；编辑旧记录不误触门禁；China 不出现无效购买 / 导出。
- [ ] 原图可查看完整长图，缩放正常；无图不用示例图顶替。
- [ ] 删除票证需要确认并取消提醒；云备份删除说明对象正确。
- [ ] 登录失败、列表失败、上传失败、口令错误、恢复重复提示、确认导入均保留。
- [ ] 备份权益检查前不允许免费用户执行上传 / 恢复；已授权的操作不因改版被隐藏。
- [ ] Play 商品加载、购买取消 / pending / 成功 / 失败及恢复购买都有清晰反馈。
- [ ] Release 没有调试开关、商品 ID、Base URL、WorkManager 文案。
- [ ] TalkBack 能找到并读出所有按钮和状态，触控区域 ≥48 dp。
- [ ] 现有 CSV、PDF、送修材料、小组件、通知点击、隐私和备案入口没有回归。

为改变过的业务映射补有意义的测试（临期筛选边界、无保修字段清空、保存失败恢复状态）。不要给每个颜色常量写快照测试。构建前确认没有另一个助手同时运行 Gradle；遇到缓存锁先协调，不删除缓存或杀掉未知 Java 进程。

可用 PowerShell 命令按项目实际环境运行：

```powershell
.\gradlew.bat :app:assembleChinaDebug :app:assemblePlayDebug
.\gradlew.bat :app:testChinaDebugUnitTest :app:testPlayDebugUnitTest
```

任务名若受实际 flavor 配置影响先查 `:app:tasks`。设计阶段不需要构建 Release，也不需要使用正式签名密钥。

完成后交付：修改文件概览、六屏实际截图、已验证行为、仍未验证的环境限制、与规范不同的明确理由。不要只声称“更现代了”。
