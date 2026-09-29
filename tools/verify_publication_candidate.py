#!/usr/bin/env python3
"""Audit the isolated source tree before assigning a release tag or uploading."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ALLOWED_JARS = re.compile(r"(^|/)gradle/wrapper/gradle-wrapper\.jar$")
PRIVATE_MARKERS = ("/build/", "/run/", "/local-plans/", "/local-workspaces/")
FUTURE_MARKERS = ("平台扩展性审阅与路线图", "第二款内置游戏", "More games are planned")
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)|\b(?:href|src)=[\"']([^\"']+)[\"']")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_publication_candidate.py <repository-directory>", file=sys.stderr)
        return 2
    root = Path(sys.argv[1]).resolve()
    manifest = root.parent / "file-manifest.json"
    rows = json.loads(manifest.read_text(encoding="utf-8"))
    paths = {row["path"] for row in rows}
    errors: list[str] = []
    for rel in sorted(paths):
        file = root / rel
        if not file.is_file():
            errors.append(f"missing: {rel}")
            continue
        normalized = "/" + rel.replace("\\", "/")
        if any(marker in normalized for marker in PRIVATE_MARKERS):
            errors.append(f"private directory: {rel}")
        if (file.suffix.lower() == ".ogg" and not rel.startswith("learning-soundpacks/")) or file.suffix.lower() in {".zip", ".exe", ".dll", ".class", ".log"}:
            errors.append(f"generated/dependency file: {rel}")
        if file.suffix.lower() == ".jar" and not ALLOWED_JARS.search(rel):
            errors.append(f"unexpected jar: {rel}")
        if file.suffix.lower() not in {".md", ".html", ".toml", ".json", ".properties"}:
            continue
        body = file.read_text(encoding="utf-8", errors="replace")
        if any(marker in body for marker in FUTURE_MARKERS):
            errors.append(f"private roadmap reference: {rel}")
        if file.suffix.lower() not in {".md", ".html"}:
            continue
        for match in LINK.finditer(body):
            raw = (match.group(1) or match.group(2)).strip().split(" ")[0]
            if raw.startswith(("#", "mailto:", "codex://")):
                continue
            parts = urlsplit(raw)
            if parts.scheme or raw.startswith("//"):
                continue
            target = (file.parent / unquote(parts.path)).resolve()
            if not target.is_relative_to(root) or not target.exists():
                errors.append(f"broken link: {rel} -> {raw}")
    for required in ("README.md", "README.en.md", "build.gradle", "common/src/main/resources/logo.png",
                     "fabric/build.gradle", "forge/build.gradle", "src/main/java/com/jokernan/craftycards/CraftyCards.java",
                     "tools/soundpack_maker.py", "versions/targets.json"):
        if required not in paths:
            errors.append(f"required source missing: {required}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Candidate audit passed: {len(paths)} files; learning audio only, no dependencies or broken local links")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
