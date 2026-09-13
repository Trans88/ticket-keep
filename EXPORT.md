# Pro 导出（PDF / CSV）

## 权益（Pro only）

- **详情页 → ⋮ → 导出 PDF**：单条票证字段（商家、金额、购买日、保修到期、备注、识别原文）+ 收据缩略图（有图则嵌入，缺失则注明路径）。
- **列表页 → ⋮ → 导出全部 CSV**：UTF-8 **带 BOM**（`\uFEFF`），Excel 可直接打开；列含 Ticket 实体字段及可读日期/金额。
- **非 Pro**：不导出，跳转现有 **Paywall**。
- 导出后走系统分享（`ACTION_SEND` + 已有 FileProvider，缓存目录 `cache/exports/`）。
- 中文文件名示例：`票证记_导出_yyyyMMdd_HHmm.pdf` / `.csv`。
- 实现：系统 `PdfDocument`，**无** iText 等重型依赖。

## 如何测试

1. **非 Pro**：详情「导出 PDF」、列表「导出全部 CSV」→ 应进入 Paywall，无文件写出。
2. **Pro**（Debug「模拟开通」或真机订阅）：导出成功 → 分享面板；snackbar「已导出 PDF/CSV」。
3. CSV 用 Excel / WPS 打开，确认中文不乱码（BOM）。
4. PDF 含中文与缩略图（或缺失提示）。
5. 确认 `FREE_TICKET_LIMIT` 仍为 **10**；未改 OCR / Billing 购买流 / Room schema / Worker。

## 关键文件

- `export/TicketPdfExporter.kt`、`export/TicketCsvExporter.kt`、`export/ExportShareHelper.kt`
- `DetailScreen` / `DetailViewModel`、`ListScreen` / `ListViewModel`
- `TicketDao.getAll()`、`TicketRepository.getAllTickets()`
- `res/xml/file_paths.xml`（`cache-path` exports）
