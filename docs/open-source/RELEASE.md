# 版本分支与发布流程

本分支固定为 **neoforge / Minecraft 1.21.1**。发布说明与版本标签以该版实际功能和验收记录为准。

## 分支模型

- `main` 是集成分支，保留全部加载器与全部 Minecraft 版本的源码。
- 每个发布目标一个分支，命名为 `<加载器>-mc<版本>`。分支由主线的 [分支脚本](../../tools/prepare-version-branches.ps1) 建出后，把该目标用不到的文件裁剪掉，形成**独立可构建的快照**（本分支即如此）。
- 分支之间是独立产品线，各自维护自己的 `mod_version`、构建产物和发布 tag。
- **同步规则：只 cherry-pick，不 merge。** 公共修复从 `main` cherry-pick 到需要的版本分支；版本专属修复在分支上提交后，用 `git cherry-pick -x <提交>` 搬回 `main` 或其它分支。
- **不要把版本分支 merge 进 `main`，也不要把 `main` merge 进版本分支。** 版本分支裁剪掉的文件会被 git 理解为“要删除”，一次 merge 就会删掉 `fabric/`、`forge/` 和 `versions/` 下的适配层；两边都改过的 `README.md` 还会冲突。
- 为了让 cherry-pick 干净：一个提交只做一件事；不要把“改代码”和“改本分支专属文件”（`TARGET.md`、`build-target.ps1`、`common/gradle/minecraft-target.gradle`、`README.md`）混在同一个提交里。

## 发版步骤

1. 冻结待发布源码，完成 [开源资料审阅](README.md) 与素材授权核对。
2. 在本分支修改 `gradle.properties` 的 `mod_version` 并提交。每个分支独立递增，不必与其它版本对齐。
3. 构建并验证：Windows 跑 `powershell -ExecutionPolicy Bypass -File .\build-target.ps1`；其它平台跑 `./gradlew build -PmcTarget=1.21.1`。确认 jar 中不含 GameTest 类或 `.ogg`，`META-INF/neoforge.mods.toml` 的 Minecraft / NeoForge 版本区间正确。
4. 运行单测与 `runGameTestServer`；有条件再跑一轮真实客户端验收，人工看图听音并记录差异和已知限制。
5. 生成 jar 的 SHA-256，保留构建日志与验收证据。
6. 打 tag 并推送：`git tag -a v<mod_version>-neoforge-mc1.21.1 -m "..."`，然后 `git push origin v<mod_version>-neoforge-mc1.21.1`。**tag 一旦推送不要移动**，发错了就发下一个版本号。
7. 在 GitHub 建 Release，附上 jar 和 `SHA256SUMS.txt`，说明支持的 Minecraft/加载器组合与验证级别；CurseForge 上传同一个 jar。

## 下载文件安排

Release 附上按 Minecraft 版本与加载器命名的安装 jar、`crafty-cards-soundpack-maker-1.0.1.zip` 和三个带音频的 1.21.1 学习包（另有合集）。制作工具 zip 内有图形界面脚本、Windows 启动器、中文说明、音乐包格式与 Harness 提示词。音乐包的来源与授权风险见 [音频权利说明](../../release-materials/AUDIO-RIGHTS.md)。同一 Release 附 `SHA256SUMS.txt`，并明确指出每个 jar 的 Minecraft/加载器组合与验证级别。

## 仓库内容

GitHub 源码仓库保留三端源码、共用资源、构建脚本、测试、Gradle wrapper（含 `gradlew` 与 `gradlew.bat`）、文档和验收证据；不跟踪下载的依赖库、编译产物、测试实例、音频包及本机工作目录。主线统一源码提交打 `v1.0.0` 标签；各目标分支从该提交建立，再各自按上面的规则发版。

本文件描述的是发布流程；分支、tag 与 Release 的实际状态以 GitHub 仓库为准。
