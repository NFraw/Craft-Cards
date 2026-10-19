# 贡献指南

感谢关注 Crafty Cards。本分支固定为 **neoforge / Minecraft 1.21.1**，构建入口与产物见 [README](README.md)。

## 提交 PR 前必读

- **PR 的 base 必须是本分支 `neoforge-mc1.21.1`，不要选 `main`。** 本分支是从 main 裁剪出的单版本快照，提到 `main` 会显示大量文件被删除。
- 一个 PR 只做一件事；合并用 **Squash and merge**，一个 PR 对应一个提交，方便 `cherry-pick` 到其它版本分支。
- 不要把“改代码”和“改本分支专属文件”（`TARGET.md`、`build-target.ps1`、`common/gradle/minecraft-target.gradle`、`README.md`、本文件）混在同一个提交里。
- 版本分支之间只 cherry-pick，不要互相 merge，也不要 merge 回 `main`。
- 不提交 `build/`、`run*/`、`.ogg`、本地配置或 IDE 文件。
- **Issue/PR 模板只存放在 `main`**，本分支不复制；CI 由本分支的 `.github/workflows/build.yml` 调用 main 的可复用 workflow。

## 构建与自测

Windows：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-target.ps1
```

Linux / macOS 或任意平台：

```bash
./gradlew build -PmcTarget=1.21.1
```

提交前至少跑一次 `build`（包含单测）；能跑桌面时再按 [验收脚本](docs/自动化验收.md) 完成一轮真实客户端对局。

## 报告问题

提交问题时说明 Minecraft、加载器、模组版本和重现步骤；画面问题附 GUI 缩放、窗口/全屏分辨率与截图，音频问题附包 id 和 `latest.log` 相关行。上传前删去私人路径、账号、服务器地址和世界敏感数据。安全漏洞请按 [安全说明](SECURITY.md) 私密报告。

## 代码约定

代码修改尽量先在 `common/` 解决共用行为，仅把注册、网络/输入/渲染钩子与版本 API 差异放进加载器或 `versions/`。纯逻辑错误补有意义的单测；客户端渲染问题提供实际截图；涉及会话关闭、物品结算或私有手牌时验证服务端权威和多人路径。不要把测试通过写成“所有版本都无问题”。

## 更多

仓库分工、文件归属、跨版本同步和新增分支步骤见 main 的 [仓库结构与维护职责](https://github.com/NFraw/Craft-Cards/blob/main/docs/open-source/REPO-STRUCTURE.md)；全局贡献规则见 [main 的 CONTRIBUTING](https://github.com/NFraw/Craft-Cards/blob/main/CONTRIBUTING.md)。
