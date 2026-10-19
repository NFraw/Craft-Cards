# 仓库结构与维护职责

本仓库是“**一个集成主线 + 每个版本一个产品分支**”的结构。本页说明每个文件应该放在哪、谁负责什么，以及新增版本分支的步骤。

## 两条线

| | `main`（默认分支） | 版本分支 `<加载器>-mc<版本>` |
|---|---|---|
| 定位 | 集成分支、仓库门面、配置中心 | 单版本产品线 |
| 源码 | `common/`、`src/`、`fabric/`、`forge/`、`versions/` 全量 | 该目标裁剪后的源码快照 |
| 文档 | 全仓库 README、CONTRIBUTING、SECURITY、CREDITS、docs/ | 本目标的 README、TARGET.md、版本说明 |
| GitHub 配置 | Issue/PR 模板、可复用 CI workflow | 只放一个调用 CI 的 `build.yml` |
| 发布 | **不发布 jar** | 负责构建、打 tag、发 Release |
| 版本号 | 公共源码的 `mod_version` 只作默认值 | 每个分支独立递增 |

## 文件归属

| 路径 | main | 版本分支 | 原因 |
|---|---|---|---|
| `.github/ISSUE_TEMPLATE/` | ✅ 唯一来源 | ❌ | GitHub 只读取默认分支的 Issue 模板，其它分支的模板不会生效 |
| `.github/pull_request_template.md` | ✅ 唯一来源 | ❌ | 同上，PR 模板必须放在默认分支 |
| `.github/workflows/build-reusable.yml` | ✅ 唯一来源 | ❌ | 真正的构建逻辑，供所有分支调用 |
| `.github/workflows/build.yml` | ✅ 调用自身 | ✅ 调用 main | Actions 按代码所在分支执行，版本分支必须有一个入口文件 |
| `README.md` | ✅ 仓库总览 + 分支索引 | ✅ 本目标说明 | 两边读者不同 |
| `CONTRIBUTING.md` | ✅ 全局规则 | ✅ 简短版，链接回 main | 避免 35 份全文不同步 |
| `SECURITY.md` | ✅ 权威版本 | 可保留副本或链接 | 报告渠道全仓库一致 |
| `LICENSE` | ✅ | ✅ 保留副本 | 分发合规 |
| `CREDITS.md` | ✅ | 可保留副本 | 素材来源说明 |
| `TARGET.md` | ❌ | ✅ | 只有版本分支需要声明固定目标 |
| `build-target.ps1` | ❌ | ✅ | 单目标构建入口 |
| `tools/` | ✅ 全部脚本 | 可选（发布相关） | 公共工具留在 main |
| `docs/` | ✅ 全部文档 | 可选（只放版本说明） | 避免文档重复 |

## 职责

**main 负责：**

1. 公共代码（`common/`、通用资源、加载器适配层）的开发和验证；
2. 唯一一份 Issue/PR 模板；
3. 唯一一份可复用 CI（`build-reusable.yml`）；
4. 全仓库文档、分支索引、发布规则；
5. **不直接产出发布 jar**，发布一律从版本分支构建。

**版本分支负责：**

1. 该 Minecraft 版本 + 加载器的源码快照与必要裁剪；
2. 自己的 `TARGET.md`、`build-target.ps1`、README；
3. 自己的 `mod_version` 与发布 tag；
4. 自己的 `build.yml`（10 行调用 main 的可复用 workflow）。

## 同步规则（只 cherry-pick）

| 改动类型 | 在哪里改 | 怎么同步 |
|---|---|---|
| 公共逻辑 / 通用资源 | `main` | `git cherry-pick -x <sha>` 到需要的版本分支 |
| 某个版本专属修复 | 该版本分支 | `git cherry-pick -x <sha>` 回 `main` 或其它分支 |
| 纯文档（全仓库） | `main` | 一般不需要同步到分支 |
| 本分支专属文件 | 各自分支 | **不要跨分支 cherry-pick** |

**禁止 merge**：版本分支裁掉了 main 上的 `fabric/`、`forge/` 和现代版本适配层，`git merge` 会把这些当成“删除”执行。两个方向都不要 merge。

为了让 cherry-pick 干净：

- 一个提交只做一件事；
- 不要把“改代码”和“改本分支专属文件”（`TARGET.md`、`build-target.ps1`、`README.md`、`CONTRIBUTING.md`、`common/gradle/minecraft-target.gradle`）混在同一个提交里；
- 公共修复尽量不碰版本分支才有的文件。

## 新增一个版本分支

```powershell
# 1. 从 main 切出
git switch -c neoforge-mc1.21.12 main

# 2. 删掉该目标不需要的加载器/版本目录（保留 common/、src/ 和需要的 versions/ 覆盖）

# 3. 写本分支声明（参考已有分支的 TARGET.md / build-target.ps1 / README）

# 4. 生成 CI 调用文件
.\tools\init-branch-ci.ps1 -Loader neoforge -McTarget 1.21.12

# 5. 提交并推送
git add .
git commit -m "Prepare neoforge-mc1.21.12 source snapshot"
git push -u origin neoforge-mc1.21.12
```

推送后到 GitHub 上：

1. 给新分支开分支保护（Require status checks 选中 `build`）；
2. 在 README 的分支索引表里加一行；
3. 第一次发版时改 `gradle.properties` 的 `mod_version`，打 `v<版本>-neoforge-mc1.21.12` tag。

## GitHub 设置（不在文件里，需要网页操作）

- **Settings → General → Pull Requests**：只允许 Squash merging；打开 Automatically delete head branches。
- **Settings → Rules / Branches**：`main` 要求 PR + 状态检查；一个人维护时不要勾 Require approvals（自己批不了自己的 PR）；禁止 force push。
- **版本分支**：建议同样要求状态检查，允许维护者直接 push（方便发版）。
- **Issues → Labels**：至少建 `bug`、`enhancement`、`needs-info`、`confirmed`、`wontfix`、`duplicate`、`good first issue`、`help wanted`，以及 `mc:1.21.1`、`loader:neoforge` 这类目标标签。
- **Issues → Milestones**：一个发布目标一个 milestone。
- **Settings → Actions → General**：允许 Actions；首次贡献者的 workflow 默认需要人工 Approve，保持默认即可。

## 为什么模板只放 main

GitHub 文档明确说明：

- Issue 模板存放在默认分支，其它分支的模板不会对贡献者生效；
- PR 模板同样必须在默认分支创建。

所以版本分支里的 `.github/ISSUE_TEMPLATE/` 和 `pull_request_template.md` 不会生效，属于冗余。唯一需要每个分支各放一份的是 **workflow**，因为 Actions 从代码所在分支读取并执行它。

参考：[About issue and pull request templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/about-issue-and-pull-request-templates)
