# 父代理须验证（本 box 未跑 Gradle / 无模拟器）

## 必须编译

```powershell
.\gradlew.bat :app:assembleChinaDebug :app:assemblePlayDebug
.\gradlew.bat :app:testChinaDebugUnitTest :app:testPlayDebugUnitTest
```

将 `/workspace/ui-ux-v2-out/app/src/main/java/...` 覆盖到工程对应包路径后再编。

## 建议截图对照（注明尺寸 / 字号 / 渠道 / 深浅色）

1. 票证首页：临期摘要 + 底栏三槽，确认**无**列表 FAB
2. 快捷筛选：即将到期 / 未过期 / 未设保修；跨午夜后 SOON 区间是否按当天刷新
3. 详情：摘要在上、图在下、底栏主按钮
4. 编辑：保修「不设置」保存后字段清空；非法金额字段错误；保存失败不转圈
5. 我的：容量 `n/10` 或 Pro 不限；无 up 箭头（Tab）
6. 云备份：Play 非 Pro → 付费墙；China 非 Pro → 说明页不循环
7. Paywall：价格来自 Billing；China 无购买按钮；无商品 ID 用户可见文案
8. 深色模式与 320dp / 200% 字号无横向溢出

## 已知需人工注意

- `ListViewModel` 同时 observe 搜索结果与全量列表（摘要）；数据量大时关注性能，可后续改 DAO 聚合
- 高级筛选「草稿取消不应用」未做独立草稿层（仍即时写入 filter，与改版前一致）；规范理想态可二期补
- `SettingsScreen` 新增 `SettingsViewModel`（Hilt）；确认模块已扫描该包
- `MainActivity` / `ListScreen` 签名增加 `addTrigger`；合并时勿漏
- Preview / TalkBack / 真机通知权限流未在本环境验证
