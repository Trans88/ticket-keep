# Pro CSV 导入 / 备份恢复

## 权益（Pro only）

- **列表 ⋮ → 从 CSV 导入**：系统选文件（SAF），UTF-8（兼容带 BOM），表头兼容本应用「导出全部 CSV」。
- **非 Pro** → Paywall；**china** 渠道与导出一样隐藏入口（或引导 Play）。
- 策略：**一律新插入**（忽略 CSV 中的 `id`）；本地不存在的「图片路径」清空，可稍后手补图。
- 导入前预览：将新增 N 条 / 跳过 M 行；确认后写入 Room。
- 免费额度：非 Pro 不可用；Pro 不限条数。

## 必要列

- 必须含表头 **`商家`**；其余导出列可选，未知列忽略。
- 优先读：`金额_分`、`购买日_epochDay`、`保修月数`、`保修到期_epochDay`、`备注`、`识别原文`、时间戳列。

## 如何测试

1. Pro 下先「导出全部 CSV」，再清空或换机后「从 CSV 导入」→ 预览 N 条 → 确认 → 列表出现。
2. 非 Pro → 点导入进 Paywall。
3. 坏表头（无「商家」）→ 中文报错，不崩。
4. `chinaDebug`：无导入入口。
5. 单测：`TicketCsvImporterTest` + 全量 `testPlayDebugUnitTest`。

## 相关文件

- `export/TicketCsvImporter.kt`、`TicketCsvExporter.kt`
- `ListScreen` / `ListViewModel`
- `IMPORT.md`（本文）、`EXPORT.md`
