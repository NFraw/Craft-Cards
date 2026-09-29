# 验证范围与证据

本页区分“编译/规则测试通过”“自动真实客户端对局通过”与“人工视觉/听觉确认”。不能用前者代替后者。NeoForge 1.21.1 的上机实测截图是视觉对照基准。

现有自动场景和 GameTest 验证的是**斗地主及其跨加载器适配**。

## 自动验证

```powershell
# 先运行基准：公共规则、NeoForge GameTest、一个服务端与三个真实客户端
.\gradlew.bat test runGameTestServer
.\tools\run-acceptance-matrix.ps1 -Versions 1.21.1 -Loaders neoforge -Fullscreen

# 再对指定版本的三种加载器运行完整对局；Forge 1.21.2 会标记不支持
.\tools\run-acceptance-matrix.ps1 -Versions 1.21.11 -Loaders neoforge,fabric,forge -Fullscreen

# 最后重新生成本地单页结果
.\tools\generate-acceptance-dashboard.ps1
```

自动流程每组启动一个专用服务器、三个真实客户端，检查放桌、配置筹码、三人入局、叫分、选牌、出牌/过牌、地主正常胜利和筹码守恒；检查私有手牌快照、倒计时、模型、世界渲染回调、音乐包事件资源及资源重载。截图覆盖叫分、HUD、选牌、结算和多个配置页；`-Fullscreen` 额外采集窗口/全屏对比。失败、超时和未运行在结果页独立标出。

**当前结果以 [本地验收总览](../evidence/multi-version-2026-09-27/README.md) 与原始 `build/acceptance-matrix/` 记录为准。** 该总览在被重新生成后会变化；旧版 [2026-09-27 构建交付记录](../evidence/modern-versions-2026-09-27/README.md) 只证明当时 122 项公共单测与构建，不能当作最新的三人实机证据。NeoForge 基线原有 35 项 GameTest；现代版本没有统一移植该 GameTest 套件。

## 人工发布门槛

逐个安装目标 jar 至干净客户端和专用服务器，核对 mod 元数据、加载器依赖、客户端联机与服务端启动。以 NeoForge 1.21.1 为参照，在窗口与全屏、GUI 自动缩放下看 HUD 手牌比例、牌面锐度、底牌位置、世界持牌遮挡、配置界面文字；再实际听音乐包的常用语音与 BGM，确认声道与音量。自动测试确认“资源事件存在”，不确认扬声器响、内容正确或听感正常。还应人工测非正常结束、掉线、无筹码娱乐局、农民胜利、存档重进及常见整合包冲突；当前自动场景主要是地主正常胜利。

报告问题时给出 Minecraft/加载器/模组包版本、操作步骤、GUI 缩放、窗口或全屏分辨率、相关截图与 `latest.log`；先清理私人目录、账号和服务器地址。现有自动化命令和结果字段详见 [多版本自动化验收](../自动化验收.md)。
