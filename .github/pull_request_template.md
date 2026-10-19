<!-- 提交前请先读 CONTRIBUTING.md 的「提交 PR 前必读」。 -->

## 改了什么

<!-- 一句话概括；如果是修 bug，写清触发条件 -->

## 目标分支

- [ ] 只影响某个 Minecraft/加载器组合：base 选对应版本分支（如 `neoforge-mc1.21.1`），**不是 `main`**
- [ ] 改公共逻辑：base 是 `main`，并说明需要 cherry-pick 到哪些版本分支
- [ ] 没有把版本分支的裁剪差异（删除 `fabric/`、`forge/`、`versions/`）带进这个 PR

## 自测

- [ ] `./gradlew build`（Windows：`.\gradlew.bat build`）通过，默认目标 1.21.1
- [ ] 改动涉及其它目标时，说明了用哪些 `-PmcTarget` 验证过
- [ ] 玩法 / 结算 / 网络改动补了单测，或说明了为什么没有
- [ ] 客户端改动附了截图或录像
- [ ] 没有提交 `build/`、`run*/`、`.ogg`、本地配置、IDE 文件

## 关联 Issue

Fixes #
