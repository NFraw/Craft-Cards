# 贡献指南

感谢关注 Crafty Cards。请先阅读 [安装与游玩](docs/open-source/INSTALL.md)、[开发与架构](docs/open-source/DEVELOPMENT.md) 和 [验证范围](docs/open-source/TESTING.md)。本版提供三人联机斗地主及 1.21–1.21.11 多加载器适配；Forge 1.21.2 不在支持矩阵。

## 提交 PR 前必读（多版本仓库）

- **先确定改动属于哪一类**：
  - 只影响某个 Minecraft/加载器组合 → Fork 后切到对应**版本分支**（如 `neoforge-mc1.21.1`），PR 的 base 也选它，**不要选 `main`**；
  - 改公共逻辑 → PR 到 `main`，合并后再 `cherry-pick` 到需要它的版本分支。
- **版本分支是裁剪过的单版本快照**，和 `main` 的差异包含“文件被删除”。把版本分支的改动提到 `main`，PR 里会出现几十个删除文件——发现这种情况请改成 cherry-pick。
- **一个 PR 只做一件事**；合并用 **Squash and merge**，一个 PR 对应一个提交，方便跨分支 cherry-pick。
- 版本分支之间只 cherry-pick，不互相 merge，也**不要 merge 回 `main`**。规则见 [版本分支与发布流程](docs/open-source/RELEASE.md)。
- **Issue/PR 模板只放在 `main`**（GitHub 只读默认分支），不要在版本分支复制；版本分支只保留一个调用 main 可复用 CI 的 `build.yml`。
- 不提交 `build/`、`run*/`、`.ogg`、本地配置或 IDE 文件。

每个 PR 会自动运行构建与单测；CI 失败时先看 Actions 日志里的失败任务，再按需补测试或修代码。文件该放哪个分支、新增版本分支怎么做，见 [仓库结构与维护职责](docs/open-source/REPO-STRUCTURE.md)。

## 构建与自测

Windows：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```bash
./gradlew build
```

默认目标是 NeoForge 1.21.1；验证其它目标用 `-PmcTarget=<版本>`。提交前至少跑一次 `build`（包含单测）；能跑桌面时再按 [验收脚本](docs/自动化验收.md) 完成一轮真实客户端对局。

## 报告问题

提交问题时说明 Minecraft、加载器、模组版本和重现步骤；画面问题附 GUI 缩放、窗口/全屏分辨率与截图，音频问题附包 id 和 `latest.log` 相关行。上传前删去私人路径、账号、服务器地址和世界敏感数据。安全漏洞请按 [安全说明](SECURITY.md) 私密报告。

## 代码约定

代码修改尽量先在 `common/` 解决共用行为，仅把注册、网络/输入/渲染钩子与版本 API 差异放进加载器或 `versions/`。纯逻辑错误补有意义的单测；客户端渲染问题提供实际截图；涉及会话关闭、物品结算或私有手牌时验证服务端权威和多人路径。不要把测试通过写成“所有版本都无问题”。
