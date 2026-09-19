# 加密云备份（Pro）

- 设置 → 云备份：注册/登录 → 设置备份口令 → 上传 → 列表 → 恢复（确认后新增导入）→ 删除/登出
- 默认 Base URL：`https://api.trans88.cn`（`BuildConfig.BACKUP_BASE_URL`；设置页可覆盖）
- cleartext：`app/src/main/res/xml/network_security_config.xml` + Manifest `networkSecurityConfig` / `usesCleartextTraffic`
- 首版备份包含 tickets.csv + 本地可读图片；恢复策略为新增导入，不静默全量覆盖
- JWT：EncryptedSharedPreferences；备份口令不上传、不持久化
