# 发行渠道（productFlavor）

票证记使用 `store` 维度两个 flavor：

| Flavor | BuildConfig | 行为 |
|--------|-------------|------|
| **play**（默认） | `PLAY_BILLING_ENABLED=true`，`SHOW_PRO_PURCHASE=true`，`SHOW_PRO_EXPORT=true` | Google Play Billing 购买/恢复；导出 PDF/CSV（Pro） |
| **china** | 上述均为 `false` | 不连接 Billing；隐藏开通/恢复与导出入口；Paywall 提示使用 Google Play 版；免费 10 条仍生效 |

开关集中在 `com.ticketkeep.app.channel.ChannelConfig`。

## 打包

在项目根目录（JDK 17 / jbr-17）：

```bat
REM Google Play
gradlew.bat :app:assemblePlayRelease

REM 国内渠道
gradlew.bat :app:assembleChinaRelease
```

Debug：

```bat
gradlew.bat :app:assemblePlayDebug
gradlew.bat :app:assembleChinaDebug
```

Android Studio：Build Variants 选 `playDebug` / `chinaDebug` / `playRelease` / `chinaRelease`。

## 单测

带 flavor 后请跑：

```bat
gradlew.bat :app:testPlayDebugUnitTest
```

（`china` 变体同样可测：`:app:testChinaDebugUnitTest`）

## 验证清单

### play
1. 冷启动会尝试连接 Billing  
2. 列表有 Pro 入口；详情/列表有导出菜单  
3. 非 Pro 导出进 Paywall，可点开通/恢复  

### china
1. 冷启动不连接 Billing  
2. 无 Pro 皇冠入口；无「导出 PDF / 导出全部 CSV」  
3. 满 10 条仍可进 Paywall，但文案说明国内包无法内购，引导 Play 版  
4. 免费 OCR/提醒等本地功能正常  
