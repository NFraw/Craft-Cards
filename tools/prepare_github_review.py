#!/usr/bin/env python3
"""Create an isolated, reviewable GitHub publication candidate; never push or publish."""

from __future__ import annotations

import hashlib
import html
import json
import re
import subprocess
from datetime import datetime
from pathlib import Path
from shutil import copy2
from urllib.parse import quote


ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
ROOT_FILES = {
    ".gitignore", "AGENTS.md", "CHANGELOG.md", "CONTRIBUTING.md", "CREDITS.md",
    "LICENSE", "README.md", "README.en.md", "SECURITY.md", "build.gradle", "gradle.properties",
    "gradlew.bat", "settings.gradle",
}
SOURCE_DIRS = {"common", "fabric", "forge", "gradle", "src", "versions", "release-materials", "learning-soundpacks"}
DOCS_EXCLUDE = ("docs/ReferenFiles/", "docs/superpowers/", "docs/plans/")
DOCS_FILES_EXCLUDE = {
    "docs/项目开发指南.md", "docs/多加载器改造方案.md",
    "docs/每桌筹码配置与判负-实施规格.md", "docs/交付测试报告.md",
    "docs/平台扩展性审阅与路线图.md",
}
TOOLS_EXCLUDE = {"tools/import_audio.py"}
PRIVATE_ROOTS = {".git", ".gradle", ".idea", ".claude", ".mimocode", ".pytest_cache", "build", "run"}
PRIVATE_PARTS = {"__pycache__", ".pytest_cache", ".gradle", "build", "run"}


def selected(rel: str) -> bool:
    parts = Path(rel).parts
    if not parts or parts[0] in PRIVATE_ROOTS or any(part in PRIVATE_PARTS for part in parts):
        return False
    if rel in ROOT_FILES:
        return True
    if parts[0] in SOURCE_DIRS:
        return True
    if parts[0] == "tools":
        return rel not in TOOLS_EXCLUDE
    if parts[0] == "docs":
        return rel not in DOCS_FILES_EXCLUDE and not rel.startswith(DOCS_EXCLUDE)
    return False


def candidate_files() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT, check=True, capture_output=True,
    )
    paths = {raw.decode("utf-8") for raw in result.stdout.split(b"\0") if raw}
    return sorted(rel for rel in paths if selected(rel) and (ROOT / rel).is_file())


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def review_page(rows: list[dict], destination: Path) -> None:
    groups: dict[str, list[dict]] = {}
    for row in rows:
        groups.setdefault(row["path"].split("/")[0], []).append(row)
    sections = []
    for group, items in sorted(groups.items()):
        links = "".join(
            f'<li><a href="repository/{quote(item["path"])}">{html.escape(item["path"])}</a>'
            f'<small>{item["bytes"]:,} B</small></li>' for item in items
        )
        sections.append(
            f'<details><summary><strong>{html.escape(group)}</strong><span>{len(items)} 个文件</span></summary>'
            f'<ul>{links}</ul></details>'
        )
    page = f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Crafty Cards · GitHub 拟发布目录审阅</title><style>
:root{{font-family:system-ui,'Microsoft YaHei',sans-serif;color:#e6e9e3;background:#111b17}}*{{box-sizing:border-box}}
body{{margin:0;line-height:1.6}}main{{width:min(1100px,calc(100% - 32px));margin:auto;padding:42px 0 90px}}
h1{{font-size:clamp(2rem,5vw,3.2rem);line-height:1.15}}h2{{margin-top:36px;color:#f4d58b}}
.lead{{color:#c5d2c4;font-size:1.08rem}}.card{{padding:18px 22px;background:#203227;border:1px solid #46634b;border-radius:13px;margin:12px 0}}
.warn{{background:#3b3025;border-color:#8c693b}}a{{color:#f2c76f}}details{{background:#1c2b22;border:1px solid #3a5540;border-radius:10px;margin:10px 0}}
summary{{cursor:pointer;padding:12px 16px;display:flex;justify-content:space-between;gap:12px}}summary span,small{{color:#aabbac}}
ul{{margin:0;padding:0 18px 16px 32px}}li{{padding:4px 0;overflow-wrap:anywhere}}small{{margin-left:12px}}
input{{width:100%;padding:11px;background:#0e1812;color:#eee;border:1px solid #64836b;border-radius:8px;font-size:1rem}}
code{{background:#ffffff16;padding:2px 5px;border-radius:4px}}.muted{{color:#aabbac}}
</style></head><body><main>
<p class="muted">本地审阅稿 · 尚未上传</p><h1>Crafty Cards GitHub 拟发布目录</h1>
<p class="lead">下面的 <code>repository/</code> 是从当前工作区独立复制的候选源码树，共 {len(rows)} 个文件。点开分类查看真实文件。它不会自动成为 Git 提交，也不会触发上传。</p>
<div class="card"><strong>目录内容</strong><p>三端源码与版本适配、Gradle wrapper、共享资源、测试与工具、当前文档、验收证据、发布文案，以及三个带音频的学习用音乐包。<a href="REVIEW-NOTES.md">阅读完整审阅说明</a>。</p></div>
<div class="card warn"><strong>权利提示</strong><p>学习用音乐包包含来源与公开分发授权未核清的录音。详见 <a href="repository/release-materials/AUDIO-RIGHTS.md">音频权利说明</a>；如有侵权，请联系下架。</p></div>
<div class="card"><strong>已排除</strong><p>本机运行目录、构建缓存、IDE/助手配置、历史设计草稿和含私人绝对路径的旧音频导入脚本。原始 NeoForge 回归日志中的工作区路径已在此候选副本里脱敏。</p></div>
<h2>浏览文件</h2><input id="q" type="search" placeholder="按路径筛选，例如 fabric、soundpack、docs/evidence" aria-label="按路径筛选">
<div id="tree">{''.join(sections)}</div>
<script>const q=document.getElementById('q');q.addEventListener('input',()=>{{const s=q.value.trim().toLowerCase();for(const d of document.querySelectorAll('details')){{let shown=0;for(const li of d.querySelectorAll('li')){{const ok=li.textContent.toLowerCase().includes(s);li.hidden=!ok;shown+=ok}}d.hidden=!shown;d.open=!!s}}}});</script>
</main></body></html>"""
    (destination / "index.html").write_text(page, encoding="utf-8")


def main() -> None:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = BUILD / f"github-review-{stamp}"
    repository = destination / "repository"
    repository.mkdir(parents=True, exist_ok=False)
    rows = []
    for rel in candidate_files():
        source = ROOT / rel
        if not source.resolve().is_relative_to(ROOT.resolve()):
            raise ValueError(f"Source escapes workspace: {rel}")
        target = repository / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        copy2(source, target)
        if rel == "docs/evidence/forge-poc-2026-09-27/neoforge-regression.txt":
            body = target.read_text(encoding="utf-8", errors="replace")
            for original, replacement in (
                (str(ROOT), "<workspace>"),
                (ROOT.as_posix(), "<workspace>"),
                (quote(ROOT.as_posix(), safe="/:"), "<workspace>"),
                (str(Path.home()), "<user-home>"),
                (Path.home().as_posix(), "<user-home>"),
            ):
                body = body.replace(original, replacement)
            target.write_text(body, encoding="utf-8")
        elif rel == "docs/open-source-preview.html":
            body = target.read_text(encoding="utf-8")
            body = body.replace("../build/acceptance-overview.html",
                                "evidence/multi-version-2026-09-27/README.md")
            body = body.replace("查看本机验收结果", "查看基线验收证据")
            body = body.replace("打开本地验收单页", "查看基线验收证据")
            target.write_text(body, encoding="utf-8")
        elif rel in {"docs/open-source/INSTALL.md", "docs/open-source/TESTING.md"}:
            body = target.read_text(encoding="utf-8")
            body = body.replace("../../build/acceptance-overview.html",
                                "../evidence/multi-version-2026-09-27/README.md")
            target.write_text(body, encoding="utf-8")
        elif rel == "versions/README.md":
            body = target.read_text(encoding="utf-8")
            body = body.replace("../build/acceptance-overview.html",
                                "../docs/evidence/multi-version-2026-09-27/README.md")
            body = body.replace("本地生成的 [验收总览]", "已归档的 [验收记录]")
            target.write_text(body, encoding="utf-8")
        elif rel == "docs/evidence/multi-version-2026-09-27/README.md":
            body = target.read_text(encoding="utf-8")
            body = re.sub(r"\[jar\]\(\.\./\.\./\.\./[^)]+\.jar\)", "构建后生成", body)
            target.write_text(body, encoding="utf-8")
        elif rel == "docs/斗地主玩法与实现介绍.md":
            body = target.read_text(encoding="utf-8")
            body = body.replace("[TODO.md](TODO.md)", "[验证与已知边界](open-source/TESTING.md)")
            body = body.replace("[交付测试报告.md](交付测试报告.md)",
                                "[验证与已知边界](open-source/TESTING.md)")
            target.write_text(body, encoding="utf-8")
        elif rel == "README.md":
            body = target.read_text(encoding="utf-8")
            body = body.replace("[docs/交付测试报告.md](docs/交付测试报告.md)",
                                "[验证与已知边界](docs/open-source/TESTING.md)")
            target.write_text(body, encoding="utf-8")
        elif rel == "docs/open-source/README.md":
            body = target.read_text(encoding="utf-8")
            body = body.replace(
                "`docs/open-source-preview.html` 的“本机验收结果”入口指向被 Git 忽略的 `build/acceptance-overview.html`，用于这次本地审阅。正式公开页面前，应移除这个本机专用链接或发布一份不含私人信息的静态验收报告并更新链接。",
                "本候选目录的单页预览已改链至 `docs/evidence/` 的归档摘要；本机动态验收总览仍需在开发工作区运行脚本生成，不随源码上传。")
            target.write_text(body, encoding="utf-8")
        rows.append({"path": rel, "bytes": target.stat().st_size, "sha256": digest(target)})
    (destination / "file-manifest.json").write_text(
        json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    notes = """# GitHub 拟发布目录审阅

`repository/` 是独立的本地候选副本，`index.html` 可按目录浏览，`file-manifest.json` 记录每个文件的 SHA-256。没有创建提交、配置远端或上传。
候选副本中的本机验收链接已改指归档摘要，历史 jar 链接改为“构建后生成”；原工作区文件保持原样。

## 纳入

- 根目录说明、许可、构建配置和三端 Gradle wrapper；`common/`、`src/`、`fabric/`、`forge/`、`versions/` 与开发/测试工具。
- 当前文档与单页介绍、`docs/evidence/` 截图和结果、`release-materials/` 发布图文，以及 `learning-soundpacks/` 中三个带音频的学习样本。

## 排除

- `.git/`、`.claude/`、IDE/缓存、所有 `build/` 与 `run*/` 开发实例、临时产物和正式 jar；只保留构建所需的 Gradle wrapper 启动 jar。
- `soundpacks/` 中其他本地开发样本；公开的三个学习包单独整理在 `learning-soundpacks/`。
- `docs/ReferenFiles/`、`docs/superpowers/`、`docs/plans/` 与过期的历史设计/交付草稿。
- `tools/import_audio.py`：旧外部音频导入脚本含本机绝对路径，且目标是已下线的内置音频目录。

## 上传前必须处理

1. 按 `CREDITS.md` 确认继承代码、69 张相同 PNG、组合图标的权利及署名；否则替换素材。
2. 音乐包存在授权风险，已在 `release-materials/AUDIO-RIGHTS.md` 明确披露；如有侵权，请联系下架。
3. 人工看一遍证据截图是否含私人玩家名、服务器/世界信息。本副本的 NeoForge 原始回归日志已脱敏本机项目路径，但仍需复核所有日志。
4. 对此候选副本跑干净构建、链接检查和正式 jar 测试；现有测试记录不能代替发布前复测。
5. 审阅发布文案、支持矩阵、目标分支；仓库地址与上传指令已由作者给出。

本目录是发布副本；音乐包仅作结构学习样本，不表示已取得第三方音频授权。如有侵权，请联系下架。
"""
    (destination / "REVIEW-NOTES.md").write_text(notes, encoding="utf-8")
    review_page(rows, destination)
    print(destination)
    print(f"files={len(rows)} bytes={sum(row['bytes'] for row in rows)}")


if __name__ == "__main__":
    main()
