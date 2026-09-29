#!/usr/bin/env python3
"""音乐包制作程序的纯逻辑测试：包 id 生成与占用检测、键识别、文件名清洗、写盘不覆盖。

这些都是"错了以后玩家很难查"的地方，尤其是包 id：

  * 两个包共用一个 id 会让后构建的那个把前一个的 `pack.json` 顶掉——被顶掉的包在游戏里
    就"没声音了"，而文件还在、界面里还列得出来（真踩过，见文件开头的「包 id 与显示名」）；
  * 键识别错了 = 玩家按文档给文件命名却不生效，且毫无提示；
  * 覆盖了别人的音频 = 不可逆。

跑法（仓库根目录）：

    python -m pytest tools
"""
from __future__ import annotations

import json
import random
import sys
from zipfile import ZipFile
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

import soundpack_maker as m  # noqa: E402  （要先把它所在目录加进 sys.path）


def test_import_existing_directory_preserves_mapping_without_writing_output(tmp_path: Path):
    source = tmp_path / "existing"
    source.mkdir()
    (source / "pass_1.ogg").write_bytes(b"OggS-example")
    (source / "pack.json").write_text(json.dumps({
        "version": 2, "name": "旧包", "author": "作者", "description": "说明",
        "sounds": {"pass": [{"file": "pass_1.ogg", "weight": 3}]},
    }, ensure_ascii=False), encoding="utf-8")
    output = tmp_path / "output"

    meta, items = m.read_existing_pack(source)

    assert meta == {"name": "旧包", "author": "作者", "description": "说明"}
    assert items["pass"] == [{"name": "pass_1.ogg", "weight": 3,
                               "src": str(source / "pass_1.ogg"), "dir": None}]
    assert not output.exists()


def test_import_release_zip_stages_only_referenced_audio(tmp_path: Path):
    archive = tmp_path / "existing.zip"
    with ZipFile(archive, "w") as z:
        z.writestr("pack-id/pack.json", json.dumps({
            "version": 2, "name": "压缩包", "sounds": {"pass": ["pass.ogg"]},
        }, ensure_ascii=False))
        z.writestr("pack-id/pass.ogg", b"OggS-example")
        z.writestr("pack-id/unreferenced.ogg", b"OggS-unused")
        z.writestr("AUDIO-RIGHTS.md", "note")
    staged = tmp_path / "staged"
    output = tmp_path / "output"

    meta, items = m.read_existing_pack(archive, staged)

    assert meta["name"] == "压缩包"
    assert items["pass"][0]["src"] == str(staged / "pass.ogg")
    assert (staged / "pass.ogg").read_bytes() == b"OggS-example"
    assert not (staged / "unreferenced.ogg").exists()
    assert not output.exists()


def test_import_rejects_unsafe_manifest_file_name(tmp_path: Path):
    source = tmp_path / "bad"
    source.mkdir()
    (source / "pack.json").write_text(json.dumps({
        "version": 2, "name": "坏包", "sounds": {"pass": ["../escape.ogg"]},
    }, ensure_ascii=False), encoding="utf-8")

    with pytest.raises(ValueError, match="文件名"):
        m.read_existing_pack(source)


def test_gui_import_zip_populates_editor_without_generating(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    archive = tmp_path / "download.zip"
    with ZipFile(archive, "w") as z:
        z.writestr("pack-id/pack.json", json.dumps({
            "version": 2, "name": "已下载的包", "sounds": {"pass": ["pass.ogg"]},
        }, ensure_ascii=False))
        z.writestr("pack-id/pass.ogg", b"OggS-example")
    output = tmp_path / "output"
    values: dict[str, str] = {}
    app = m.SoundPackMakerApp.__new__(m.SoundPackMakerApp)
    app.items = {key: [] for key in m.KEY_ORDER}
    app._weight_vars = {}
    app._import_tempdir = None
    app._built_dir = None
    app._out_root_path = lambda: output
    app._refresh_all = lambda: None
    app._apply_filter = lambda: None
    app.log = lambda *args: None
    app.pack_name = SimpleNamespace(set=lambda value: values.__setitem__("name", value))
    app.pack_author = SimpleNamespace(set=lambda value: values.__setitem__("author", value))
    app.pack_desc = SimpleNamespace(set=lambda value: values.__setitem__("description", value))
    app.pack_id = SimpleNamespace(set=lambda value: values.__setitem__("id", value))
    monkeypatch.setattr(m.filedialog, "askopenfilename", lambda **kwargs: str(archive))

    app._on_import(True)

    assert values["name"] == "已下载的包"
    assert len(values["id"]) == 8
    assert app.items["pass"][0]["name"] == "pass.ogg"
    assert not output.exists()
    app._import_tempdir.cleanup()


def test_import_legacy_pack_migrates_to_editable_entries(tmp_path: Path):
    source = tmp_path / "old"
    source.mkdir()
    (source / "pass.ogg").write_bytes(b"OggS-example")
    (source / "pack.json").write_text(json.dumps({
        "name": "旧格式", "soundEffects": {"pass": "pass.ogg"},
    }, ensure_ascii=False), encoding="utf-8")

    meta, items = m.read_existing_pack(source)

    assert meta["name"] == "旧格式"
    assert items["pass"][0]["weight"] == 1


# --------------------------------------------------------------------------- #
# 包 id：生成与占用检测
# --------------------------------------------------------------------------- #


def test_generated_id_is_eight_hex_chars():
    rng = random.Random(1234)
    for _ in range(50):
        pid = m.generate_pack_id(rng)
        assert len(pid) == m.PACK_ID_LENGTH
        assert set(pid) <= set(m.PACK_ID_ALPHABET)
        # 生成的 id 必须能过游戏侧那套字符集校验（否则目录名白生成）
        assert m.validate_pack_id(pid) == pid


def test_generated_ids_do_not_repeat():
    """同一台机器上放几十个包是常态，随机 id 之间不该撞车。"""
    rng = random.Random(7)
    ids = {m.generate_pack_id(rng) for _ in range(500)}
    assert len(ids) == 500


def write_manifest(pack_dir: Path, name: str) -> None:
    pack_dir.mkdir(parents=True, exist_ok=True)
    (pack_dir / m.MANIFEST_NAME).write_text(
        json.dumps({"version": 2, "name": name, "sounds": {}}, ensure_ascii=False),
        encoding="utf-8")


def test_manifest_name_reads_display_name(tmp_path: Path):
    write_manifest(tmp_path / "abc", "  甲的包  ")
    assert m.manifest_name(tmp_path / "abc") == "甲的包"        # 两端空白会去掉
    assert m.manifest_name(tmp_path / "nope") is None           # 目录不存在
    (tmp_path / "broken").mkdir()
    (tmp_path / "broken" / m.MANIFEST_NAME).write_text("{ 不是 json", encoding="utf-8")
    assert m.manifest_name(tmp_path / "broken") is None         # 清单坏了 → 当作没有


def test_pick_free_pack_id_skips_occupied_dirs(tmp_path: Path):
    """占用判定只看"目录是否存在"：连没有清单的残留目录也不碰（避免和残留混在一起）。"""
    rng = random.Random(3)
    taken = m.generate_pack_id(random.Random(3))     # 同一个种子 → 第一次会生成这个值
    (tmp_path / taken).mkdir()
    picked = m.pick_free_pack_id(tmp_path, rng=random.Random(3), log=lambda *_: None)
    assert picked != taken
    assert not (tmp_path / picked).exists()


def test_pick_free_pack_id_gives_up_loudly(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    """一直撞（概率上不可能，但别死循环）：报错而不是硬写同一个目录。"""
    (tmp_path / "aaaaaaaa").mkdir()
    monkeypatch.setattr(m, "generate_pack_id", lambda rng=None: "aaaaaaaa")
    with pytest.raises(ValueError):
        m.pick_free_pack_id(tmp_path, log=lambda *_: None)


def test_claim_pack_dir_takes_empty_dir(tmp_path: Path):
    pid, pack_dir = m.claim_pack_dir(tmp_path, "aaaaaaaa", "甲的包")
    assert pid == "aaaaaaaa"
    assert pack_dir == tmp_path / "aaaaaaaa"


def test_claim_pack_dir_keeps_own_pack_on_rebuild(tmp_path: Path):
    """同一个显示名 = 自己的包在重新构建：写回同一个目录（不堆出一堆新包）。"""
    write_manifest(tmp_path / "aaaaaaaa", "甲的包")
    assert m.claim_pack_dir(tmp_path, "aaaaaaaa", "甲的包")[0] == "aaaaaaaa"


def test_claim_pack_dir_switches_id_instead_of_clobbering(tmp_path: Path, monkeypatch):
    """目录里是**别人的包** → 换新 id，且那个包的清单原样留着（这是这条逻辑存在的理由）。"""
    write_manifest(tmp_path / "aaaaaaaa", "乙的包")
    monkeypatch.setattr(m, "generate_pack_id", lambda rng=None: "bbbbbbbb")
    pid, pack_dir = m.claim_pack_dir(tmp_path, "aaaaaaaa", "甲的包", log=lambda *_: None)
    assert (pid, pack_dir) == ("bbbbbbbb", tmp_path / "bbbbbbbb")
    assert m.manifest_name(tmp_path / "aaaaaaaa") == "乙的包"


def test_claim_pack_dir_owned_survives_rename(tmp_path: Path):
    """本次运行已经写过这个目录 → 是我们的：中途改了显示名也照样写回原目录。"""
    write_manifest(tmp_path / "aaaaaaaa", "改名前")
    assert m.claim_pack_dir(tmp_path, "aaaaaaaa", "改名后", owned=True)[0] == "aaaaaaaa"


def test_claim_pack_dir_strict_refuses_foreign_pack(tmp_path: Path):
    """命令行显式指定 id 时（人在要目录）：这里已经有别人的包就直接报错。"""
    write_manifest(tmp_path / "aaaaaaaa", "乙的包")
    with pytest.raises(ValueError, match="乙的包"):
        m.claim_pack_dir(tmp_path, "aaaaaaaa", "甲的包", strict=True)


def test_claim_pack_dir_rejects_illegal_id(tmp_path: Path):
    with pytest.raises(ValueError):
        m.claim_pack_dir(tmp_path, "../跑出去", "甲的包")


# --------------------------------------------------------------------------- #
# 键识别、文件名、权重标记
# --------------------------------------------------------------------------- #


@pytest.mark.parametrize(("stem", "expected"), [
    ("pass", "pass"),
    ("pass_1", "pass"),
    ("bgm_playing", "bgm_playing"),
    ("sidailiangdui", "sidailiangdui"),     # 长键优先，别被 sidaier 抢走
    ("sidailiangdui_2", "sidailiangdui"),
    ("dan15", "dan15"),                     # 也别被 dan1 抢走
    ("dan1", "dan1"),
    ("BGM WIN", "bgm_win"),                 # 空格归一 + 大小写不敏感
    ("sandaiyi", "sandaiyi"),
    ("随便什么名字", None),
    ("pas", None),                          # 不是键名、也不以键名+分隔符开头
])
def test_match_key(stem: str, expected: str | None):
    assert m.match_key(stem) == expected


@pytest.mark.parametrize(("stem", "expected"), [
    ("pass_w3", ("pass", 3)),
    ("pass.w3", ("pass", 3)),
    ("pass-2-w4", ("pass-2", 4)),
    ("pass_1", ("pass_1", 1)),      # 没有 w 标记
    ("pass_w0", ("pass_w0", 1)),    # 越界（包内权重必须 ≥1）→ 当没有标记
    ("pass_w100", ("pass_w100", 1)),
])
def test_split_weight_marker(stem: str, expected: tuple[str, int]):
    assert m.split_weight_marker(stem) == expected


def test_flatten_file_name_is_game_safe():
    """包内文件名必须是游戏侧接受的 [A-Za-z0-9._-]+.ogg（否则游戏拒绝、包静默失效）。"""
    for raw in ["我的 语音(1).mp3", "../逃逸.ogg", "Pass.MP3", "  ", "a" * 200]:
        name = m.flatten_file_name(raw)
        assert m.is_acceptable_file_name(name), raw
        assert name.endswith(".ogg")


# --------------------------------------------------------------------------- #
# 写盘：绝不静默覆盖
# --------------------------------------------------------------------------- #


def write_ogg(path: Path, content: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return path


def test_gui_add_only_stages_audio_until_player_generates(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    """GUI 的添加动作不可提前创建包目录或写入音频。"""
    source = write_ogg(tmp_path / "source" / "pass_1.ogg", b"example")
    output = tmp_path / "soundpacks"
    app = m.SoundPackMakerApp.__new__(m.SoundPackMakerApp)
    app.items = {key: [] for key in m.KEY_ORDER}
    app._commit_all_weights = lambda: None
    app._refresh_key = lambda key: None
    app._apply_filter = lambda: None
    app.log = lambda *args: None
    monkeypatch.setattr(m.filedialog, "askopenfilenames", lambda **kwargs: [str(source)])

    app._on_add("pass")

    assert app.items["pass"] == [{"name": "pass_1.ogg", "weight": 1,
                                  "src": str(source), "dir": None}]
    assert not output.exists()


def test_gui_build_writes_staged_audio_after_generate_action(tmp_path: Path, monkeypatch: pytest.MonkeyPatch):
    """已暂存的映射在最终生成动作之后才变成包目录和清单。"""
    source = write_ogg(tmp_path / "source" / "pass_1.ogg", b"example")
    output = tmp_path / "soundpacks"
    app = m.SoundPackMakerApp.__new__(m.SoundPackMakerApp)
    app.items = {key: [] for key in m.KEY_ORDER}
    app.items["pass"].append({"name": "pass_1.ogg", "weight": 1,
                              "src": str(source), "dir": None})
    app._commit_all_weights = lambda: None
    app._refresh_all = lambda: None
    app.log = lambda *args: None
    app._out_root_path = lambda: output
    app.pack_id = SimpleNamespace(get=lambda: "aaaaaaaa", set=lambda value: None)
    app.pack_name = SimpleNamespace(get=lambda: "审查测试包")
    app.pack_author = SimpleNamespace(get=lambda: "Tester")
    app.pack_desc = SimpleNamespace(get=lambda: "")
    app._built_dir = None
    monkeypatch.setattr(m.messagebox, "showinfo", lambda *args: None)

    assert not output.exists()
    app._on_build()

    manifest = output / "aaaaaaaa" / "pack.json"
    assert json.loads(manifest.read_text(encoding="utf-8"))["sounds"]["pass"] == ["pass_1.ogg"]
    assert (manifest.parent / "pass_1.ogg").read_bytes() == b"example"


def test_place_reuses_identical_file_and_renames_different_one(tmp_path: Path):
    """重复构建不该堆出一堆副本，但同名不同内容也绝不覆盖。"""
    src = write_ogg(tmp_path / "src" / "pass.ogg", b"same-bytes")
    other = write_ogg(tmp_path / "src2" / "pass.ogg", b"different-bytes")
    writer = m.PackWriter(tmp_path / "pack", log=lambda *_: None)

    assert writer.place(src) == "pass.ogg"
    assert writer.place(src) == "pass.ogg"                  # 同一个源 → 复用，不生成 _2
    assert writer.place(other) == "pass_2.ogg"              # 同名不同内容 → 换名字
    assert (tmp_path / "pack" / "pass.ogg").read_bytes() == b"same-bytes"


def test_write_manifest_omits_empty_keys_and_keeps_order(tmp_path: Path):
    writer = m.PackWriter(tmp_path / "pack", log=lambda *_: None)
    writer.ensure_dir()          # 契约：写清单前先建目录（两个入口都这么调）
    path = writer.write_manifest(
        name="甲的包", author="人", description="",
        sounds={"pass": [("pass.ogg", 1)], "bgm_win": [("win.ogg", 3)]})

    data = json.loads(path.read_text(encoding="utf-8"))
    assert data["version"] == 2
    assert data["name"] == "甲的包"
    # 权重 1 写成裸字符串，其余写成对象；没条目的键整条省略
    assert data["sounds"]["pass"] == ["pass.ogg"]
    assert data["sounds"]["bgm_win"] == [{"file": "win.ogg", "weight": 3}]
    assert list(data["sounds"]) == ["pass", "bgm_win"]      # 按 KEY_ORDER 排，便于 diff
