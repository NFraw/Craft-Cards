# 开发与架构 · forge / Minecraft 1.21.6

## 环境与目录

需要 JDK 21 与 Windows PowerShell。本分支仅构建 forge / Minecraft 1.21.6。加载器源码位于 `forge/`，共用源码位于 `common/`，累计版本覆盖位于 `versions/`。依赖版本为 Forge 1.21.6-56.0.9。

纯规则和布局函数留在公共层；加载器层负责注册、网络、客户端输入和渲染。服务端持有权威牌局引擎并按接收者构建可见快照。

## 构建与验证

从仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-target.ps1
```

脚本只构建 Minecraft 1.21.6，执行 `build` 中的测试与检查，核对安装用 JAR `forge/build/mc-1.21.6/libs/crafty_cards-forge-mc1.21.6-1.0.0.jar`。不能用其他 `-PmcTarget` 从本分支生成另一版本。源码修改后先关闭开发客户端再重新构建。

## 开发边界

- 修改斗地主规则时在 `game/DDZEngine` 等纯逻辑类补有意义的单测；**新游戏使用独立规则引擎**，不要把规则并入 `DDZEngine`。旧网络动作枚举使用 ordinal 编码，只能向末尾追加。
- 对桌子会话的每个关闭路径，给所有相关参局者、离开者、观战者、配置界面持有者发送终止快照；返还或结算筹码必须在清座位前完成。
- 双端加载的类不得在方法体直接引用客户端类。网络 handler 在主线程处理；每位玩家的可见快照单独生成。
- 版本特有 API 放入 `versions/<版本段>/` 或对应加载器适配层，不在公共游戏规则中堆积版本分支。
- 牌面 HUD、全屏缩放、底牌位置与客户端 mixin 要做真实客户端复核，编译/单测无法证明渲染正确。

更完整的内部约束见 [AGENTS.md](../../AGENTS.md)，当前玩法见 [斗地主玩法与实现介绍](../斗地主玩法与实现介绍.md)，历史规划文档仅供溯源。
