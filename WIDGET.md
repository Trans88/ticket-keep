# 临期桌面小组件（MVP）

## 选条规则

仅展示 **有保修截止日期**（`warrantyEndEpochDay != null`）的票证，合计最多 **5** 条：

1. 最多 **2** 条已过期：按到期日 **降序**（最近过期优先），状态标红「已过期」
2. 剩余名额：未过期（含今天到期），按到期日 **升序**（越临期越靠前）
3. 展示顺序：已过期在前，其后未过期

副文案：`已过期` / `今天到期` / `还剩 X 天`；同时显示到期日（`yyyy年M月d日`）。

无匹配票证时显示空态文案。

## 技术选型

- **Jetpack Glance**（`glance-appwidget`）
- Room：`TicketDao.getAllWithWarranty()`
- Hilt：`WidgetEntryPoint` + `EntryPointAccessors`（Glance 无构造注入）

## 交互

| 操作 | 行为 |
|------|------|
| 点整卡标题区 /「打开」 | 打开 App 列表，并尽量预选「未过期」筛选 |
| 点单条 | 打开对应详情（`EXTRA_TICKET_ID`，与保修通知一致） |

## 刷新时机

1. **系统周期**：`updatePeriodMillis = 1_800_000`（30 分钟；系统可能进一步节流）
2. **即时**：`TicketRepository.saveTicket` / `deleteTicket` 成功后经 `WidgetRefresher` 调用 `ExpiringTicketsWidget.updateAll`
3. 桌面首次添加 / 尺寸变化：系统 `onUpdate`

## 如何添加

1. 安装/运行含本功能的构建（Play debug 即可）
2. 长按桌面空白处 → **微件 / 小组件 / Widgets**
3. 找到 **票证记** → **票证记 · 临期**（描述：展示即将到期与最近过期的保修票证）
4. 拖到桌面；可按需调整大小（约 4×3 格起）

部分国产桌面入口在「桌面编辑」或「插件」。

## 已知限制

- 无保修截止日的票证不出现在小组件
- 系统对 `updatePeriodMillis` 有节流；跨日「还剩 X 天」可能最多延迟约一周期才变，增删改会即时刷
- 若列表筛选模块未合入，点卡片仍打开列表，但不预选「未过期」
- Glance 圆角在部分旧系统上可能不明显
- 未做保养日程 / 多尺寸独立布局 / 深色主题定制（跟随 GlanceTheme 默认）

## 相关文件

- `widget/ExpiringTicketsWidget*.kt`、`WidgetRefresher.kt`、`ExpiringTicketsSelector.kt`
- `res/xml/expiring_tickets_widget_info.xml`
- `WIDGET.md`（本文）
