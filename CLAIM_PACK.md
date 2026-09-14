# Pro 理赔 / 送修材料包 PDF

## 权益（Pro only）

- **详情 ⋮ → 生成送修材料包**：一页或多页 PDF，固定结构：
  1. 封面：理赔/送修材料包 + 生成日期
  2. 票证摘要：商家、金额、购买日、保修到期、备注
  3. 凭证图（有则嵌入；过长自动分页缩放）
  4. 识别原文（如有）
  5. 页脚免责：仅整理自用户本地资料，不构成理赔承诺或法律意见
- **非 Pro** → Paywall；**china** 与导出一样隐藏入口。
- 系统分享；文件名示例：`票证记_送修材料包_商家_yyyyMMdd.pdf`。
- 仍保留「导出 PDF」通用导出。

## 不做

自动判定能否理赔、对接保险公司、云端生成、多票合并一包。

## 如何测试

1. Pro：详情 ⋮ → 生成送修材料包 → 分享面板；打开 PDF 核对五段结构与免责。
2. 非 Pro → Paywall。
3. `chinaDebug`：无该菜单。
4. 无图 / 长图 / 长 OCR 不崩。
5. 单测：`ClaimPackPdfExporterTest`；全量 `testPlayDebugUnitTest`。

## 相关文件

- `export/ClaimPackPdfExporter.kt`
- `DetailScreen` / `DetailViewModel`
- `CLAIM_PACK.md`（本文）、`EXPORT.md`
