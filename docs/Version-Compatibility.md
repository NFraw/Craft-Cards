# Minecraft 版本适配

此页记录已完成的 **1.21 与 1.21.1 × NeoForge、Fabric、Forge** 轻量扩展。后续 **1.21.2 至 1.21.11** 的分代适配、构建工具与逐版本验收见 [现代版本适配](../versions/README.md)。三个加载器分别产出 jar；不承诺一个 jar 跨加载器或跨 Minecraft 版本，也未验证跨加载器混服。

## 构建矩阵

| Minecraft | NeoForge | Fabric API（Loader 0.19.5） | Forge |
|---|---|---|---|
| 1.21 | 21.0.167 | 0.102.0+1.21 | 51.0.33 |
| 1.21.1（默认） | 21.1.236 | 0.116.17+1.21.1 | 52.1.0 |

Java 21。构建依赖组合定义在 `common/gradle/minecraft-target.gradle`。使用 `'-PmcTarget=1.21'` 切换；未登记目标在配置阶段直接拒绝。登记的现代目标需要各自的适配层与构建证据，不因出现在列表中就视为已通过实机测试。

从仓库根目录执行：

```powershell
./gradlew.bat '-PmcTarget=1.21' build
./gradlew.bat -p fabric '-PmcTarget=1.21' build
./gradlew.bat -p forge '-PmcTarget=1.21' build
./gradlew.bat '-PmcTarget=1.21' runGameTestServer
```

1.21 产物分别位于 `build/mc-1.21/libs/`、`fabric/build/mc-1.21/libs/`、`forge/build/mc-1.21/libs/`；文件名包含加载器与 `mc1.21`。省略参数或传入 `-PmcTarget=1.21.1` 保持既有 `build/libs/` 路径和文件名。

开发运行目录按版本隔离。三客户端完整牌局验收依次执行（需要桌面图形环境，独占本机 25576 端口）：

```powershell
./gradlew.bat '-PmcTarget=1.21' -PpocSmoke pocSmoke --no-configuration-cache
./gradlew.bat -p fabric '-PmcTarget=1.21' -PpocSmoke pocSmoke --no-configuration-cache
./gradlew.bat -p forge '-PmcTarget=1.21' -PpocSmoke pocSmoke --no-configuration-cache
```

不要在开发客户端运行时构建。验收创建独立服务器与三个客户端；结果位于各目标的 `build[/mc-1.21]/poc/`。验收模组只在显式启用 `pocSmoke` 时参与，发布 jar 不包含它。

Forge 51 的验收服务器曾在世界保存、服务端线程退出后仍被空闲后台线程保持存活。Forge 验收模组会等待服务端线程结束，再按 PASS/FAIL 结果退出其独占 JVM；此逻辑不进入发布包，也不修改正常服务器的退出行为。

## 为什么 1.21 能低成本共用代码

斗地主引擎、计分、布局、快照及公共资源保持共用；三个加载器都使用 Mojang 映射，Minecraft 1.21 和 1.21.1 在本项目用到的主要接口上可以共用源码。网络发送、注册、输入事件、渲染阶段和资源包注入继续由加载器薄层承接。

实际发现并处理了以下加载器差异：

- NeoForge 1.21 的自动事件订阅不会像较新加载器一样推断 MOD 总线，客户端初始化与音乐资源包事件必须显式指定 `EventBusSubscriber.Bus.MOD`。只构建或只跑服务端测试无法发现这个客户端启动错误。
- Forge 51 使用无参入口构造器；改为在构造器内取得加载上下文，同时兼容 Forge 52。
- Forge 51 开发环境按官方 MDK 严格锁定 `jopt-simple 5.0.4`，避免传递依赖升级到 6.x 后启动器找不到 `jopt.simple` 模块。

版本元数据收紧到实际构建目标，资源生成和测试输出也按版本分开，避免 1.21.1 的缓存元数据混入 1.21 包。

## 其他版本的技术边界

| 版本段 | 当前判断 | 本项目需要处理的变化 |
|---|---|---|
| 1.20.5 / 1.20.6 | 可以移植，但不属于此次少量改动范围 | 回退顶点构建 API、HUD 时间参数及资源目录约定；需要独立客户端适配与资源转换 |
| 1.20.1 / 1.20.4 | 需要单独回移 | 还涉及物品 NBT 与 Data Components、网络及实体同步接口差异 |
| 1.21.2 / 1.21.3 | 可以移植，不能直接共编译 | 实体 RenderState、物品/方块注册键、交互返回值等变化，直接影响卡牌实体和物品 |
| 1.21.4 及后续 | 应按 API 代际建立版本适配层 | 在上述变化之上还引入新的物品模型定义；54 张牌的模型选择和 HUD 渲染必须重新验证，不能只改依赖版本 |

这些是针对当前源码的技术评估，不是宣称其他版本永远不能适配。后续应继续复用纯逻辑，在明确的 Minecraft API 边界拆分客户端/物品注册适配与资源生成，避免在核心牌局代码里到处判断版本。

官方变化说明：[1.21 / 1.21.1](https://fabricmc.net/2024/05/31/121.html)、[1.21.2 / 1.21.3](https://fabricmc.net/2024/10/14/1212.html)、[1.21.4](https://fabricmc.net/2024/12/02/1214.html)。Fabric 文章同时记录了原版 Minecraft API 变化，因此相应边界也影响 NeoForge / Forge。

## 验收范围

2026-09-27 六组组合均已通过构建、各 122 项单测以及独立服务器 + 三客户端完整牌局。NeoForge 两个版本各通过 35 项 GameTest。原有 120 项单测保留，另有 2 项 HUD 手牌叠放回归测试。

逐组结果、截图与安装包 SHA-256 见 [本次验收记录](evidence/multi-version-2026-09-27/README.md)。

三人验收覆盖放桌、配置筹码、入局、叫分、HUD 手牌和倒计时、滚轮/左键选牌、出牌/过牌、正常结算及筹码守恒；检查牌面模型和世界渲染回调，也检查 config 音乐包加载及资源重载后音效事件存在。

音效事件存在不等于已经完成耳听或音频采集验收。当前验收不声称每条 BGM/语音实际播放均已逐一确认，也不覆盖任意整合包冲突或历史存档迁移。
