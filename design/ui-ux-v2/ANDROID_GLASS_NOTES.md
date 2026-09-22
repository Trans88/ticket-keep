# Android 悬浮玻璃导航修正

## 实现边界

- `MainActivity` 用同一个 `HazeState` 连接页面内容与底栏，采样源不包含底栏本身。详情页、键盘弹出时不采样。
- `HomeBottomBar` 使用 22 dp 背景模糊、独立前景、低强度噪点、方向性反光与细内沿。不是把按钮本身模糊，也不逐帧生成屏幕位图。
- 保留暖白 / 森林绿品牌色；按钮最小高度 56 dp、圆角 28 dp，窄屏按可用宽度分配空间。
- 字体倍率超过 1.3 时中心按钮显示短标签“存票证”，无障碍描述仍为“存一张票证”。
- 列表底部留白使用实际测得的胶囊高度 + 系统导航区 + 10 dp 悬浮间隙 + 16 dp 余量。页头处理状态栏，Scaffold 不再重复扣除上下安全区。
- 系统减少透明度 / 高对比设置与硬件加速能力参与降级判断，设置变化与回到前台会重新检查。

## 依赖与降级

固定 Haze **1.1.1**，对应当前 Kotlin 2.0.21 / Compose 1.7.x 工具链；没有为这次视觉修改升级整个工程。参考 [版本 API](https://chrisbanes.github.io/haze/1.1.1/api/haze/dev.chrisbanes.haze/index.html) 与 [版本源码](https://github.com/chrisbanes/haze/tree/1.1.1)。未来升级 Haze 时需要一起核对 Kotlin / Compose 兼容性。

使用 `HazeDefaults.blurEnabled()` 的兼容性策略，不强行开启库默认排除的 Android 12 / API 31 模糊。旧系统、辅助功能降级和非硬件加速环境使用不透明的 `#F3F7EF` / `#26392D`。

这是 Android 上的磨砂玻璃实现，不宣称完整复刻 Apple 的光学折射；当前也没有单独实现网页原型的 `saturate(155%)` 色彩滤镜。

## 验证方法

```powershell
.\gradlew.bat :app:assembleChinaDebug :app:testChinaDebugUnitTest
.\gradlew.bat :app:connectedChinaDebugAndroidTest
```

- `HomeBottomBarLayoutTest`：普通 / 三键安全区、大字体实测高度、初始高度、显示路由。
- `HomeBottomBarRenderTest`：320 dp 合成条纹背景上的真正平滑效果、降级差异、浅深色、200% 字体和按钮交互。不会访问用户票证或云服务。
- 渲染测试将 `glass-light.png`、`glass-fallback.png`、`glass-dark.png`、`glass-large-text.png` 输出到测试设备上应用的 external files 目录，供人工核验。

模拟器通过不等于所有真机通过。发布前仍需在目标手机上检查连续滚动、系统三键导航、键盘、底部面板层级和后台恢复。

## 本次验证（2026-09-22）

- `assembleChinaDebug` 成功；85 项单元测试通过。
- Android 14 / API 34 模拟器渲染测试通过：细条纹平滑、背景变化实时更新、与不透明降级的差异、Tab/添加动作、深浅色和 200% 字体。
- 人工查看渲染截图，并打开真实 MainActivity 核对首页、键盘弹出时隐藏底栏，以及添加面板正确覆盖底栏。
- 本机 Android 17 / API 37 模拟器上的旧 Espresso 测试框架因 `InputManager.getInstance` 缺失失败；未将其视为应用渲染失败，也未为此升级工程工具链。
- 最新截图位于本地 `app/build/glass-qa/`（构建产物，不入库）。Gradle 连接测试可能卸载测试应用并移除 external files；需保留图片时可手动安装测试 APK、运行 instrumentation 后再导出。
