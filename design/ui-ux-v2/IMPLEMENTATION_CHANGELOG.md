# TicketKeep UI/UX v2 — 变更清单

相对路径均相对 `app/src/main/java/com/ticketkeep/app/`（输出根：`ui-ux-v2-out/`）。

## Phase A — 主题与公共组件

| 路径 | 说明 |
|---|---|
| `ui/theme/Color.kt` | 按 tokens.json 映射浅/深色语义色；保留旧薄荷别名兼容 |
| `ui/theme/Theme.kt` | M3 ColorScheme + v2 Shapes（7/12/18/22/26）；`dynamicColor` 默认 false；Spacing/Radius 对象 |
| `ui/theme/Type.kt` | brand/page/card/body/supporting/amount 字号行高；金额 tnum |
| `ui/theme/WarrantyStatus.kt` | 状态色对齐 primaryContainer / warningContainer / surfaceVariant；临期含 0..30 |
| `ui/components/PaperCard.kt` | 默认 card=22；新增 `TicketPaperCard` ticket=18；细描边零阴影 |
| `ui/components/WarrantyStatusChip.kt` | badge 圆角 7；无保修文案「未设置保修」 |
| `ui/components/SectionHeader.kt` | **新建** 分组标题 |
| `ui/components/SettingsRow.kt` | **新建** 设置行（≥48dp） |

## Phase B — 首页与底栏

| 路径 | 说明 |
|---|---|
| `ui/navigation/HomeBottomBar.kt` | 三槽：票证 \| 中心添加 FAB \| 我的；`onAdd` 回调 |
| `MainActivity.kt` | 底栏仅 list/settings；`addTrigger` 驱动 ListScreen 添加面板；settings 接隐私 |
| `ui/screens/list/ListViewModel.kt` | 全量 `soonCount`；快捷筛选 ALL/SOON/NOT_EXPIRED/NO_WARRANTY；SOON→NOT_EXPIRED+到期日[today,today+30] |
| `ui/screens/list/ListScreen.kt` | 去掉 FAB；临期摘要英雄卡；快捷芯片；纸感行；Pro 入口移至 overflow（Play 仍可买） |

## Phase C — 详情与编辑

| 路径 | 说明 |
|---|---|
| `ui/screens/detail/DetailScreen.kt` | 摘要置顶、原图下移、底栏主动作（送修/编辑）、更多菜单含编辑/导出/删除 |
| `ui/screens/edit/EditScreen.kt` | 分组表单；保修三模式；dirty 返回确认；IME 安全底栏 |
| `ui/screens/edit/EditViewModel.kt` | `WarrantyInputMode.None` 清空保修字段；金额校验；`save` try/catch/finally；空白新建不填今天 |

## Phase D — 我的 / 备份 / Pro

| 路径 | 说明 |
|---|---|
| `ui/screens/settings/SettingsViewModel.kt` | **新建** 观察条数与 Pro |
| `ui/screens/settings/SettingsScreen.kt` | 「我的」分组：票证册容量 / 数据备份 / 通知 / 关于；Tab 无 up-nav；Pro 入口保留 Play |
| `ui/screens/backup/CloudBackupScreen.kt` | 保留 Pro 门禁与恢复确认；China 非 Pro 说明+返回，不循环付费墙；Base URL 仍仅 Debug |
| `ui/screens/paywall/PaywallScreen.kt` | 价值点 + Billing 真价；去硬编码 ¥/商品 ID 用户文案；China 无购买说明 |

## 未改（刻意）

- `ChannelConfig` / Billing / Repository / DAO / OCR / 加密 / 路由常量
- `TicketListFilter.kt`（SOON 复用现有桶+日期范围）
- `PrivacyPolicyScreen` / Widget / CloudBackupViewModel / PaywallViewModel
- 无 Gradle 构建（box 无 Android SDK）
