# Fabric 1.21.1 POC

## 构建与运行

要求 Java 21。NeoForge 仍在仓库根目录构建；Fabric 是独立的 Loom 1.7.4 工程，使用 Mojang 官方映射、Fabric Loader 0.19.5 和 Fabric API 0.116.17+1.21.1。

```powershell
# NeoForge：构建、122 单测、35 GameTest
.\gradlew.bat build runGameTestServer

# Fabric：构建和同一套 122 单测
.\fabric\gradlew.bat -p fabric build

# Fabric 开发运行，每个实例独立目录与玩家名
.\fabric\gradlew.bat -p fabric runServer
.\fabric\gradlew.bat -p fabric runClient
.\fabric\gradlew.bat -p fabric runClient2
.\fabric\gradlew.bat -p fabric runClient3
```

产物分别在 `build/libs/crafty_cards-1.0.0.jar` 和 `fabric/build/libs/crafty_cards-fabric-1.0.0.jar`。Fabric 安装时还需要 Fabric API。发布 jar 不含音乐、GameTest、自动验收代码。

开发客户端会从构建目录按需加载类；修改后先关闭实例再编译，避免重写运行中的 class 文件。

## 自动三客户端验收

```powershell
.\fabric\gradlew.bat -p fabric -PpocSmoke pocSmoke
```

需要可运行 OpenGL 客户端的桌面环境。任务先完成编译，再启动一个专用服务器与三个真实客户端；使用 `127.0.0.1:25576`，端口已占用时拒绝启动。每个 JVM 最大堆 768 MB，另需预留 Gradle、原生图形与资源内存。首次运行需要下载 Minecraft 依赖及资源；缓存齐备后可加 `--offline`。若本机 Gradle 缓存不在默认位置，可传 `--gradle-user-home <目录>`。

测试只操作 `fabric/run-smoke-server` 和 `fabric/run-smoke-client1/2/3`。这些目录专供验收使用，任务会重置其中的测试配置和结果标记，复制仓库官方音乐包，并为本机测试服务器写入 EULA 同意与离线模式配置。不要把自己的存档放进去。

服务端测试夹具只准备地面、初始物品与玩家位置。放桌使用实际物品交互；配置、加入、叫分、出牌经过正式 C2S/S2C。滚轮和左键调用原始鼠标方法，实际经过 Fabric mixin，避免仅调用 HUD 方法而漏验注入。

验收断言包括：

- 全部 54 个牌面编号保真、54 个不同模型、4 个不同牌背模型。
- 创造标签页可显示，含 3 个预期条目。
- config 目录音频包在启动与重载后仍选中；专用服务器不注入客户端资源包。
- 持物潜行右键可开房主配置；保存筹码后，三人入局各得 17 张牌。
- 叫分时隐藏底牌，出牌时隐藏对手手牌；倒计时在同一份快照期间继续减少。
- 鼠标 mixin 接管叫分、选牌并阻止破坏方块；半透明阶段 mixin 实际触发。
- 地主逐张出牌、农民过牌，直到正常胜利；三客户端都收到结算，地主手牌为 0。
- 结算发生真实筹码转移，三人背包绿宝石合计恢复为 192（入局押注期间有部分筹码暂存在物品池）。

任一断言失败或超时会使 Gradle 任务失败。成功后自动退出全部实例。日志和结果汇总在 `fabric/build/poc/`，各客户端 `screenshots/` 下保存 `01-bidding.png`、`02-playing.png`、`03-settled.png`。只有存在本次 `PASS` 标记且进程正常退出才算通过，启动成功不等于验收通过。

## 公共层与加载器边界

- `common/src/main/java`：牌局引擎、服务端会话、快照、方块/物品、HUD 与世界渲染。纯算法类保持无 Minecraft 依赖；其余公共类只依赖原版 API，不导入加载器类型。
- `common/src/main/resources`：模型、贴图、语言、配方、掉落表、图标，以及开发测试结构。两侧直接消费同一份资源，不复制生成第二份。
- `src/main/java`：NeoForge 注册、事件与网络胶水；原有 GameTest 留在这一侧。
- `fabric/src/main/java`：Fabric 注册、网络、生命周期、HUD 回调与 client mixin。
- `common/src/smoke`：Fabric、Forge 共用的实机测试逻辑；`fabric/src/smoke` 仅绑定 Fabric 生命周期。仅 `-PpocSmoke` 启用，不进入正式产物。

Fabric mixin：`MouseHandlerMixin` 拦截鼠标，`LevelRendererMixin` 在半透明层绘制后调用公共渲染器，`PackRepositoryMixin` 增加 config 音频资源包来源。全部属于 client 列表；音频注入还会检查仓库含 `ClientPackSource`，以免污染集成服务器的数据包仓库。`sources` 字段用 `@Shadow @Final @Mutable`，复制集合后写回。实体数据序列化器直接调用原版 `registerSerializer`，不需要额外 accessor。

## 此次修复与范围

原版 `ClampedItemPropertyFunction.call` 会把模型谓词返回值限制到 0～1。旧版把 0～53 的牌面编号直接写进 lambda，导致 1 以后的牌都取同一个模型；比较 0 和 1 的旧诊断无法抓住它。当前公共层显式覆盖 `call` 保留离散整数，模型 JSON 阈值不变，兼容现有资源包。验收断言在修复前于编号 2 失败。

本 POC 覆盖原版 Fabric 渲染链路下三人正常对局、HUD、倒计时、筹码结算与音频包加载。NeoForge 和 Fabric 混服、第三方渲染器/光影兼容性未纳入验收。Fabric 暂无 Mod Menu 集成和 `/craftycards` 客户端命令；本桌配置由 Shift+右键打开，其他配置可修改各实例的 config 文件后重启。

## 回归记录

2026-09-27：NeoForge 干净构建通过，120 单测无失败、无跳过，35 个必需 GameTest 全部通过；Fabric 普通配置下干净构建通过，同一套 120 单测无失败、无跳过。最终三客户端验收耗时 2 分 42 秒，四个实例全部 PASS 并正常退出。

保留的 [服务端结果](evidence/fabric-poc-2026-09-27/runSmokeServer-result.txt)、[客户端 1](evidence/fabric-poc-2026-09-27/runSmokeClient1-result.txt)、[客户端 2](evidence/fabric-poc-2026-09-27/runSmokeClient2-result.txt)、[客户端 3](evidence/fabric-poc-2026-09-27/runSmokeClient3-result.txt) 不会被清理构建目录删除。

实机截图：[叫分与手牌](evidence/fabric-poc-2026-09-27/01-bidding.png)、[桌面出牌](evidence/fabric-poc-2026-09-27/02-playing.png)、[正常结算](evidence/fabric-poc-2026-09-27/03-settled.png)。右下角 JOKER 是手持扑克的第一人称模型，桌面实际出牌为牌桌上平铺的小牌。

### HUD 叠放修正（2026-09-27）

上述首次验收截图仍有 HUD 卡牌共面的问题：所有 3D 模型都在 z=0，无法保证重叠区域的覆盖顺序。公共层现在按索引递增 GUI 深度，右牌完整覆盖左牌；选中只上移，不改变叠放顺序。焦点框位于本牌正面之前、下一张牌之后，遵守同一遮挡关系。小号重叠牌行也使用这个层级。

新增两项深度区间测试，旧实现均失败，修复后两侧各 122 单测通过，NeoForge 35 GameTest 通过。视觉复核见 [普通手牌](evidence/hud-stacking-2026-09-27/hand.png) 和 [20 张牌中选中一张](evidence/hud-stacking-2026-09-27/selected.png)。自动验收增加选中牌停留与截图，避免只验证网络和牌面编号而遗漏遮挡。
