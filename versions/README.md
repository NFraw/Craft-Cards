# 1.21.2—1.21.11 多加载器适配

目标为 1.21.2 至 1.21.11 正式版，明确排除全部 26.x。官方 Forge 没有发布 1.21.2，因此矩阵为 29 个组合。

`targets.json` 保存官方版本目录核实的依赖组合；登记目标不代表已经通过构建或实机测试。完成状态以构建日志和验收记录为准。

2026-09-27 已交付 29 个通过构建与各 122 项公共单测的测试包，逐组合结果见 [当时的交付记录](../docs/evidence/modern-versions-2026-09-27/README.md)。此后已增加三客户端自动对局与截图验收，**最新状态请看已归档的 [验收记录](../docs/evidence/multi-version-2026-09-27/README.md)**；该历史交付记录不能代表当前测试状态。HUD 视觉对比及音乐实际听音仍需人工复核。

公共玩法沿用 `common/`。有完整行为差异的类放在 `1.21.N/common/java` 或对应加载器的 `java` 目录；后续代际覆盖前面的同路径文件。机械 API 迁移集中在 `api-adapters.gradle`。生成代码只写入各目标独立的 `build/mc-版本/generated/versionJava`，不重写既有 1.21/1.21.1 源码。

现代版本的 GameTest API 需要单独移植，当前不包含旧 NeoForge GameTest 类；旧版本原有 122 单测与 35 GameTest 仍须保留。新版本交付时分开记录编译、公共单测、启动和人工实机状态。

## 构建与安装包

Java 21。Windows 下在仓库根目录运行：

```powershell
.\tools\build-version-matrix.ps1
# 也可以只构建一个组合：
.\tools\build-version-matrix.ps1 -Loaders fabric -Versions 1.21.11
# 全部 29 个组合成功后，校验哈希并打包给人工验收：
.\tools\package-version-matrix.ps1
```

脚本为 Fabric 现代版本选择 Gradle 9.2.1 / Loom 1.13.6，为 NeoForge、Forge 1.21.3–1.21.10 选择原有 Gradle 8.8。Forge 1.21.11 按官方 MDK 单独使用 Gradle 9.5.0 / ForgeGradle 7.0.40。Forge 使用独立进程，避免 ForgeGradle 跨目标复用已关闭的项目服务。1.21/1.21.1 原有构建入口保持不变。

成功的安装包复制到 `build/releases/<Minecraft版本>/<加载器>/`；每次批量构建在 `build/version-matrix/<时间>/results.json` 记录退出码、单测数量、日志路径、安装包 SHA-256。`runtime: not-tested` 不代表已经进游戏验收。构建失败时不会把残留旧 jar 当成新成功产物。

打包脚本要求每个组合的最近一次构建成功、至少 122 项公共测试无失败，且安装包哈希与构建记录一致。输出 `build/crafty-cards-1.0.0-all-loaders.zip`，附依赖版本表、安装说明及校验清单。Fabric/Forge 的 `check` 还会读取当前版本的 Minecraft 字节码，核对 mixin 注入方法和影子字段；此检查不能替代客户端启动与实机渲染验收。

## 迁移边界

| 版本段 | 适配内容 |
| --- | --- |
| 1.21.2–1.21.3 | 注册时设置资源键、实体渲染状态、交互返回值、配方材料新格式 |
| 1.21.4 | 54 张牌面的物品模型定义、四种牌背的模型组件 |
| 1.21.5 | NBT 可选值读取、UUID 编解码、物品提示与背包 tick 签名 |
| 1.21.6–1.21.8 | ValueInput/ValueOutput 存档、二维 HUD 队列、区块渲染组注入 |
| 1.21.9–1.21.10 | 物品与实体提交队列、列表行接口、鼠标事件、资源包版本范围 |
| 1.21.11 | Identifier 命名、相机接口、区块渲染采样器参数 |

三端共用斗地主引擎、服务端会话、快照、筹码结算、配置与音乐包逻辑；版本差异只进入生成源码与版本资源。新的 HUD 每张牌使用独立绘制层，避免新版 GUI 的批处理重新混排手牌。世界牌和桌面筹码仍在半透明地形绘制之后提交。

## 逐版本人工验收

每个 Minecraft / 加载器组合使用独立实例与测试存档。Fabric 还需安装 `targets.json` 对应的 Fabric API，三个加载器的 jar 不能混装。

1. 启动客户端和专用服务器，确认注册表、mixin、模型加载无错误。
2. 创造拿牌桌并合成牌桌；放置、拆除，检查掉落物。
3. 三名玩家入桌，检查各自手牌不同、HUD 逐张遮挡、焦点框、选中抬升和倒计时。
4. 叫分、合法出牌、非法出牌、过牌、正常结算；检查筹码守恒。
5. 检查离桌、掉线、拆桌、超时；旁观者能否看牌由服务端配置决定。
6. 配置牌桌后保存退出，再进世界确认房主、筹码与门槛保留。
7. 安装音乐包，选择、保存、重载，听发牌、出牌语音和各阶段 BGM；关闭再开启、切包也要检查。日志无缺失音效不等于已听音验收。

官方接口依据：[1.21.2 更新](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-2)、[Fabric 1.21.4 迁移说明](https://fabricmc.net/2024/12/02/1214.html)、[1.21.9 更新](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)。各版本实际编译使用 Mojang 官方映射与对应加载器依赖。
