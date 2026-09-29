#!/usr/bin/env python3
"""Package the three 1.21.1 learning sound packs with their audio files."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "run" / "config" / "crafty_cards" / "soundpacks"
OUTPUT = ROOT / "build" / "release-assets" / "upload"
NOTICE = ROOT / "release-materials" / "AUDIO-RIGHTS.md"
PACKS = (
    ("官方默认包", "official-default"),
    ("6e2304d0", "relaxed"),
    ("dd1a42e5", "classic-dual-voice"),
)
REVISED_DEFAULT_DESCRIPTION = "历史默认包；音频来源及公开再分发授权待核。"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pack_files(pack_id: str) -> tuple[dict, dict[str, bytes], dict]:
    directory = SOURCE / pack_id
    manifest_path = directory / "pack.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("version") != 2 or not isinstance(manifest.get("sounds"), dict):
        raise ValueError(f"Invalid music pack manifest: {pack_id}")
    references = [entry if isinstance(entry, str) else entry["file"]
                  for entries in manifest["sounds"].values() for entry in entries]
    if not references:
        raise ValueError(f"Empty music pack: {pack_id}")
    files = {}
    for name in sorted(set(references)):
        if Path(name).name != name or not name.lower().endswith(".ogg"):
            raise ValueError(f"Unsafe audio name: {pack_id}/{name}")
        data = (directory / name).read_bytes()
        if data[:4] != b"OggS":
            raise ValueError(f"Not an Ogg stream: {pack_id}/{name}")
        files[f"{pack_id}/{name}"] = data
    if pack_id == "官方默认包":
        manifest["description"] = REVISED_DEFAULT_DESCRIPTION
    files[f"{pack_id}/pack.json"] = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    summary = {
        "id": pack_id,
        "name": manifest.get("name", ""),
        "keys": len(manifest["sounds"]),
        "references": len(references),
        "audioFiles": len(files) - 1,
        "sourcePackJsonSha256": sha256(manifest_path.read_bytes()),
        "packagedPackJsonSha256": sha256(files[f"{pack_id}/pack.json"]),
        "audioSha256": {Path(name).name: sha256(data) for name, data in files.items() if name.endswith(".ogg")},
    }
    return manifest, files, summary


def write_zip(path: Path, files: dict[str, bytes]) -> str:
    with ZipFile(path, "w", ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            archive.writestr(name, data)
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt archive: {path}")
    return sha256(path.read_bytes())


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    notice = NOTICE.read_bytes()
    all_files = {"AUDIO-RIGHTS.md": notice}
    summaries = []
    for pack_id, slug in PACKS:
        _, files, summary = pack_files(pack_id)
        name = f"crafty-cards-1.21.1-{slug}-soundpack.zip"
        summary["archive"] = name
        summary["archiveSha256"] = write_zip(OUTPUT / name, {"AUDIO-RIGHTS.md": notice, **files})
        summaries.append(summary)
        all_files.update(files)
    bundle = "crafty-cards-1.21.1-three-soundpacks.zip"
    bundle_hash = write_zip(OUTPUT / bundle, all_files)
    report = {"status": "learning samples with audio; see AUDIO-RIGHTS.md", "packs": summaries,
              "bundle": bundle, "bundleSha256": bundle_hash}
    (OUTPUT / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(summaries)} sound packs and one bundle: {OUTPUT}")
    for item in summaries:
        print(f"{item['name']}: {item['keys']} keys, {item['audioFiles']} Ogg files, {item['archive']}")


if __name__ == "__main__":
    main()
