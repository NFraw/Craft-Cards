# Forge 1.21.1 POC

独立工程 `forge/` 使用 Java 21、Forge 52.1.0、ForgeGradle 6.0.54 和 Mojang 官方映射。版本基于 [Forge 官方 1.21.1 下载页](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html) 的推荐版本。

## 构建与运行

```powershell
.\forge\gradlew.bat -p forge build
.\forge\gradlew.bat -p forge runServer
.\forge\gradlew.bat -p forge runClient
.\forge\gradlew.bat -p forge runClient2
.\forge\gradlew.bat -p forge runClient3
.\forge\gradlew.bat -p forge -PpocSmoke pocSmoke
```

产物为 `forge/build/libs/crafty_cards-forge-1.0.0.jar`，只用于 Minecraft 1.21.1 的 Forge 52.1.x。客户端与服务器均需安装。不要把其他加载器的 jar 混装。开发实例使用各自的 `run-*` 目录；先关闭实例再构建。

音乐包复制到每个客户端的 `config/crafty_cards/soundpacks/`，选包配置仍在 `sounds.json`。本桌配置由潜行右键牌桌打开。其他配置可编辑 config 文件后重启；本 POC 未接入 Forge Mods 配置按钮和客户端配置命令。

## 公共层与 Forge 接缝

- 与 NeoForge、Fabric 直接共用 `common/src/main/java` 和 `common/src/main/resources`，包括引擎、快照、HUD、渲染、模型、配方与音乐包生成逻辑。
- Forge 注册器绑定 common 的内容把手，实体数据序列化器走 Forge 同步注册表。
- 网络使用严格区分方向的 Forge SimpleChannel，复用 common 的两份 StreamCodec，处理器在主线程执行，按玩家单独发送快照。
- 服务端事件转接 tick、掉线、拆桌与潜行持物交互。
- 客户端 mixin 接入原版 HUD 末尾、半透明渲染阶段、鼠标输入和 config 资源包来源；全部限定客户端，资源包注入还检查 ClientPackSource。
- Forge 52 模块加载要求每个模组的 class 与资源处于同一目录。编译输出保持独立，资源任务再复制 class 组装开发模块；只向运行类路径暴露组装目录，避免增量资源构建删除 class 或产生重复模块。
- 三客户端测试逻辑位于 `common/src/smoke/java`，各加载器只绑定生命周期回调。测试代码不进入正式 jar，jar 不含任何音频。

## 实机验收

`pocSmoke` 先完成编译，再启动一个专用服务器和三个真实客户端。使用独立 `run-smoke-*` 目录、仅监听本机的 25576 端口和测试世界，自动安装仓库官方音乐包。不要在这些目录放个人存档。

执行放桌、打开并保存筹码配置、三人入局、叫分、鼠标选牌、出牌/过牌和正常结算；断言54个独立牌面模型、牌面隐私、动态倒计时、世界渲染回调、资源包重载和筹码守恒。只在四个进程都正常退出且各自产生 PASS 时判定通过。

音频检查目前覆盖音乐包注入、音频事件加载与重载，不等同于实际播放状态或逐项听音验收。跨加载器混服、第三方渲染器和光影不在本 POC 范围内。

2026-09-27 最终实机验收：专用服务器和三个客户端全部 PASS，正常退出，耗时 2 分 11 秒。[验收记录](evidence/forge-poc-2026-09-27/verification.txt)。Forge 正式构建与资源增量构建均通过，122 项单测无失败、无跳过。NeoForge 构建与 122 项单测通过，35 项必需 GameTest 全通过。

截图：[叫分](evidence/forge-poc-2026-09-27/01-bidding.png)、[选中牌与叠放](evidence/forge-poc-2026-09-27/02-selected.png)、[桌面出牌](evidence/forge-poc-2026-09-27/02-playing.png)、[正常结算](evidence/forge-poc-2026-09-27/03-settled.png)。

Fabric 回归也通过：构建、122 项单测、专用服务器与三个真实客户端完整对局，全部正常退出（2 分 12 秒）。[三端构建检查与产物哈希](evidence/forge-poc-2026-09-27/build-checks.txt)、[Fabric 回归记录](evidence/forge-poc-2026-09-27/fabric-regression.txt)、[NeoForge 回归记录](evidence/forge-poc-2026-09-27/neoforge-regression.txt)。
