# 正式签名说明

票证记的 `playRelease` 与 `chinaRelease` 共用同一把正式签名密钥，确保应用升级、国内 App 备案和各商店分发使用一致身份。

## 本地文件

- 密钥库：`signing/ticketkeep-release.jks`
- 密码配置：`keystore.properties`
- 密钥别名：`ticketkeep-release`

密钥库和密码配置均已加入 `.gitignore`，不得提交到 Git、聊天工具或公开网盘。请将这两个文件一起复制到至少两处可靠的加密离线备份中；丢失密钥后将无法为已发布应用签署兼容升级包。

## 证书信息

- 主题：`CN=TicketKeep Release, OU=Mobile, O=TicketKeep, C=CN`
- 算法：RSA 4096 / SHA256withRSA
- 有效期：2026-09-19 至 2126-08-26
- MD5：`DF:8F:3D:EA:C5:F5:B8:99:B1:90:31:92:32:19:30:5E`
- SHA-1：`A0:CB:2C:46:57:86:2C:CB:A3:AC:5E:AE:B4:46:64:1E:82:8F:F2:E6`
- SHA-256：`DA:7C:7E:56:A7:26:1E:E4:01:73:78:BD:17:5C:5C:15:89:63:52:38:BA:50:22:B8:78:FD:FB:C4:EC:F9:13:04`

国内 App 备案填写安卓签名信息时使用 MD5 指纹；公钥可由阿里云备案智能助理从正式 APK 自动提取。

## 构建

```bat
gradlew.bat :app:assembleChinaRelease
gradlew.bat :app:assemblePlayRelease
```

只要根目录存在有效的 `keystore.properties`，两个 Release 变体都会自动使用正式签名。Debug 变体仍使用 Android 默认调试签名。

