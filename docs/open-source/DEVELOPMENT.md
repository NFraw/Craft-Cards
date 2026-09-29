# 开发与架构

## 环境与目录

需要 JDK 21 与 Windows PowerShell。仓库自带 Gradle wrapper；1.21.11 Forge 的测试启动器另需 JDK 8 构建工具链，游戏 JVM 仍为 JDK 21。首次构建会下载依赖。根目录是 NeoForge 工程，`fabric/`、`forge/` 是独立工程；三者共用 `common/src/main/java`、`common/src/main/resources`。`versions/` 保存 1.21.2 及以后按 API 代际覆盖的源码与 Gradle 适配规则，`common/gradle/minecraft-target.gradle` 登记目标依赖版本。

纯规则和布局函数留在公共层；公共层其余类只调用 Minecraft 原版接口。加载器层负责注册、网络发送、客户端输入/渲染事件及 config 资源包注入。服务端持有桌子会话与权威 `DDZEngine`，按接收者分别构建快照，避免把私有手牌发给无权查看的客户端。客户端根据快照绘制 HUD、世界手牌与桌面牌；倒计时由服务端剩余刻数在本地插值。音乐包由客户端目录生成资源包并重载。

## 单组合构建

在仓库根目录运行；`1.21.1` 是默认目标，其他版本显式传 `-PmcTarget`。编译结果会按版本隔离；正式安装包以 [构建矩阵脚本](../../tools/build-version-matrix.ps1) 收集的路径为准。

```powershell
.\gradlew.bat '-PmcTarget=1.21.1' build
.\fabric\gradlew.bat -p fabric '-PmcTarget=1.21.1' build
.\forge\gradlew.bat -p forge '-PmcTarget=1.21.1' build

.\tools\build-version-matrix.ps1 -Versions 1.21.11 -Loaders neoforge,fabric,forge
```

现代版本由脚本选择适用的 Gradle wrapper/工具链。Forge 1.21.2 不在目标矩阵，不能以“构建失败”替代“不支持”。源码修改后先关闭对应开发客户端再重编译，避免类文件在运行时被覆盖。

## 开发边界

- 修改斗地主规则时在 `game/DDZEngine` 等纯逻辑类补有意义的单测；**新游戏使用独立规则引擎**，不要把规则并入 `DDZEngine`。旧网络动作枚举使用 ordinal 编码，只能向末尾追加。
- 对桌子会话的每个关闭路径，给所有相关参局者、离开者、观战者、配置界面持有者发送终止快照；返还或结算筹码必须在清座位前完成。
- 双端加载的类不得在方法体直接引用客户端类。网络 handler 在主线程处理；每位玩家的可见快照单独生成。
- 版本特有 API 放入 `versions/<版本段>/` 或对应加载器适配层，不在公共游戏规则中堆积版本分支。
- 牌面 HUD、全屏缩放、底牌位置与客户端 mixin 要做真实客户端复核，编译/单测无法证明渲染正确。

更完整的内部约束见 [AGENTS.md](../../AGENTS.md)，当前玩法见 [斗地主玩法与实现介绍](../斗地主玩法与实现介绍.md)，历史规划文档仅供溯源。
