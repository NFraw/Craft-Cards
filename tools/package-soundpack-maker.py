#!/usr/bin/env python3
"""Create a small, dependency-free release zip for the GUI sound-pack maker."""

from __future__ import annotations

import hashlib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "build" / "release-assets" / "crafty-cards-soundpack-maker-1.0.0.zip"
FILES = {
    "soundpack_maker.py": ROOT / "tools" / "soundpack_maker.py",
    "启动音乐包制作工具.bat": ROOT / "tools" / "soundpack-maker-distribution" / "启动音乐包制作工具.bat",
    "README.md": ROOT / "tools" / "soundpack-maker-distribution" / "README.md",
    "Harness音乐包制作提示词.md": ROOT / "docs" / "Harness音乐包制作提示词.md",
    "音乐包格式.md": ROOT / "docs" / "音乐包格式.md",
    "AUDIO-RIGHTS.md": ROOT / "release-materials" / "AUDIO-RIGHTS.md",
    "LICENSE": ROOT / "LICENSE",
}


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUTPUT, "w", ZIP_DEFLATED) as archive:
        for name, source in FILES.items():
            data = source.read_bytes()
            if name == "音乐包格式.md":
                data = data.replace(b"../release-materials/AUDIO-RIGHTS.md", b"AUDIO-RIGHTS.md")
            if name == "README.md":
                data = data.replace(b"../../release-materials/AUDIO-RIGHTS.md", b"AUDIO-RIGHTS.md")
            archive.writestr(name, data)
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    print(f"{OUTPUT}\nSHA-256 {digest}")


if __name__ == "__main__":
    main()
