# Google Play Billing（票证记 Pro）

## 产品规则

| 类型 | 票证上限 | 权威来源 |
|------|----------|----------|
| 免费 | 最多 **10** 条（`FREE_TICKET_LIMIT`） | — |
| Pro | 无限 | **Google Play Billing** 购买/查询；DataStore `is_pro` 仅为缓存 |

- 订阅 productId（常量）：`ticketkeep_pro_yearly`（见 `BillingConfig.PRODUCT_ID`）
- 当前仅订阅（SUBS）；代码预留枚举扩展空间，无需一次性购买 / 家庭共享 / 导出

## Play Console 步骤摘要

1. **创建应用**（若尚未创建）并上传至少一版 **内测（Internal testing）** AAB/APK。
2. **获利 → 商品 / 订阅**：创建订阅，商品 ID 必须为 `ticketkeep_pro_yearly`（与代码一致）。
3. 配置基础方案（例如年付）与价格、地区。
4. **许可测试人员（License testers）**：添加测试 Google 账号，可用测试卡购买且通常不真实扣款。
5. 将含 Billing 的构建发布到 **内部测试轨道**，用许可测试账号加入内测并安装。
6. 真机（需 Google Play）打开应用 → 升级 Pro → 应能看到价格 →「开通 Pro」走 Play 购买页；「恢复购买」可同步已有订阅。

## 客户端验证清单

1. Android Studio → **Sync Project with Gradle Files**（确认 `billing-ktx:7.1.1`）。
2. Debug 安装到 **带 Google Play 的真机/模拟器**，用许可测试账号登录 Play。
3. 冷启动：`TicketKeepApp` 会 `startConnectionAndRefresh`，同步购买状态。
4. Paywall：有商品时显示价格；按钮「开通 Pro」「恢复购买」。
5. 购买成功后应显示「已是 Pro会员」，列表可无限添加。
6. Debug 构建仍有「模拟开通 Pro（调试）」；Release 无假开关。
7. **确认 `FREE_TICKET_LIMIT` 仍为 10**。

## 无 Play / 离线行为

| 场景 | 行为 |
|------|------|
| 模拟器无 Google Play / Billing 不可用 | 连接失败，友好中文提示；**不清除**本地 Pro 缓存；免费版仍可用（最多 10 条） |
| 查询商品失败 / 未上架内测 / 未创建订阅 | 显示：「暂未从 Google Play 获取到商品，请确认应用已上架内测且已创建订阅 ticketkeep_pro_yearly」 |
| 离线但曾是 Pro | 保留 DataStore 缓存的 `is_pro`；下次成功查询再校正 |
| 查询成功且无有效订阅 | 将 Pro 缓存置为 `false` |

## 关键实现位置

- `billing/BillingConfig.kt` — productId
- `billing/BillingManager.kt` — 连接 / 查询 / 购买 / 确认 / 恢复
- `TicketKeepApp` — 启动时连接并刷新
- `PaywallViewModel` / `PaywallScreen` — UI
- `ProPreferences` — 缓存；由 Billing 驱动

## 依赖

```
com.android.billingclient:billing-ktx:7.1.1
```

（写入 `gradle/libs.versions.toml` + `app/build.gradle.kts`，不影响既有 splashscreen 等依赖。）
