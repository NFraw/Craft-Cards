#!/usr/bin/env python3
"""音乐包制作程序：把自备的音频整理成一个「音乐包」（tkinter 图形界面 + 无界面命令行）。

=================== 音乐包是什么 ===================

模组自身**不含任何第三方音频**，音效与背景音乐都来自玩家自建的「音乐包」。
一个包 = 一个目录 + 一份清单（pack.json）：

    config/crafty_cards/soundpacks/<包id>/
        pack.json      ← 清单：每个音频键用哪些文件、各自的随机权重
        pass_1.ogg     ← 音频文件（必须是 Ogg Vorbis，Minecraft 只认这个格式）
        normal.ogg

游戏只会**读**这个目录（见 `client/SoundPack.java`），从不由游戏写出 pack.json——
所以包一律用本程序（或手写同格式的 pack.json）制作。

=================== 包 id 与显示名 ===================

  * **包 id** = 目录名，由本程序随机生成（8 位小写十六进制），**玩家不填**。
  * **显示名 / 作者** = 玩家填、也是在游戏里看到的东西（音乐包列表显示这两项）。

为什么 id 不让玩家填：一个目录里只放得下一份 `pack.json`。两个包如果共用一个 id
（两个人都取名 `my_pack` 是最容易发生的一种），后构建的那个会把前一个的清单**顶掉**——
被顶掉的包在游戏里就是"没声音了"，而文件还在、界面还列得出来，极难排查。
随机 id 让两个包撞不到一起，所以同一台机器上可以并排放好几个包随意切换。

同一份 config 目录里因此有三条规矩（`claim_pack_dir` 实现，有测试）：

  ① 包 id 只认"目录不存在"的算空闲，连没有清单的残留目录也不碰；
  ② 目录里已有**别的显示名**的包 → 换一个新 id（原包毫发无损，日志里说明一句）；
  ③ 命令行显式指定 id 时（脚本用）不换 id，那个目录里有别人的包就直接报错——
     人明确要往那儿写，就别猜。

顺带说明 id 的另一面：游戏里存的是**选用了哪个 id**（`sounds.json` 的 `activePack`），
逐条权重也按 `pack:<包id>/<文件>` 记账。所以手动改目录名 = 换了一个包：原来的选用记录
与权重覆盖都会失配（游戏会提示重新选包，不会崩）。要改包的名字请改**显示名**，别动目录名。

=================== pack.json（version 2） ===================

    {
      "version": 2,
      "name": "官方默认包",
      "author": "作者名",
      "description": "说明",
      "sounds": {
        "pass": [
          {"file": "pass_1.ogg", "weight": 3},
          "pass_2.ogg"
        ],
        "bgm_playing": ["normal.ogg"]
      }
    }

  * `sounds`：音频键 → 条目列表。键名必须来自下面的固定名单，别的键一律无效。
  * 条目可以是裸字符串 "a.ogg"（等价于权重 1），也可以是
    {"file": "a.ogg", "weight": N}，N 是正整数（本程序限制在 1~99）。
  * 同一个键给了多个文件时，游戏按权重随机挑一个播放（权重只是相对比例）。
  * 没填任何文件的键整条省略；`name`/`author`/`description` 始终写出（可为空串）。
  * 文件名必须是 `[A-Za-z0-9._-]` 且以 `.ogg` 结尾——模组用这条规则拦路径穿越与
    JSON 注入，所以本程序会把中文名、空格、括号等自动压成合法名字。

=================== 音频键（共 61 个，分三组） ===================

  音效 7：      deal 发牌 / 洗牌、bid 叫分确认、play 出牌（通用，未匹配到牌型语音时用）、
                bomb 炸弹 / 火箭、pass 过牌（不要）、win 结算胜利、lose 结算失败
  背景音乐 6：  bgm_waiting BGM：等待玩家、bgm_playing BGM：对局进行中、
                bgm_clutch BGM：有人只剩三张牌（残局紧张段）、bgm_rocket BGM：王炸之后、
                bgm_win BGM：结算胜利、bgm_lose BGM：结算失败
                （后加的这两首没配也不会有问题：游戏侧的播放顺序是
                  王炸 → 残局 → 对局进行中，依次往下找，一个都没有才不播。）
  牌型语音 48： dan1..dan15（单张：dan1=A、dan2..dan10=2..10、dan11=J、dan12=Q、
                dan13=K、dan14=小王、dan15=大王）、
                dui1..dui13（对子，编号同单张的 1..13：1=A、…、11=J、12=Q、13=K）、
                tuple1..tuple13（三条，编号同对子）、
                sandaiyi 三带一 / 三带二、shunzi 顺子、liandui 连对、feiji 飞机、
                sidaier 四带二（单）、sidailiangdui 四带二（对）、wangzha 王炸 / 火箭

  * 中文名以游戏侧 `CustomAudio.label()` 为准（那份注释写着"界面与音乐包制作程序
    共用一套说法"），所以 BGM 那一组带「BGM：」前缀：**结算音效（win/lose，一次性）
    与结算 BGM（bgm_win/bgm_lose，循环）是两组不同的键，游戏里两条都在播**；
    名字不带前缀的话，列表里就成了两行一模一样的中文名（改版前的缺陷）。
  * 中文名两两不同（有断言挡着）——列表里出现两个一样的中文名，玩家分不清哪个是哪个。

只做一部分也行：没做的键没有声音——音效回落到**原版音效**、BGM 不播（模组里一个音频
文件都没有，所以没有"内置音频"可退），牌型语音则退回通用「出牌」。

=================== 游戏里与音频有关的可配置项 ===================

制作程序只负责"包里有哪几个文件、各占多大权重"（pack.json 里只有 `file` + `weight`）；
游戏的音频界面只剩下三个旋钮（改界面时先核对这段）：

  音乐包页      总开关（关掉后音效退回原版音效、BGM 不播、音乐包也不加载）、音量、
                选用哪个包 / 重新扫描 / 各包的覆盖情况 / 音乐包目录

  BGM 声道与「压低原版背景音乐」没有界面入口：要改就改 config/crafty_cards/sounds.json（重启生效）。

  键的优先级：牌型语音键有候选 → 用它；否则通用 `play`/`bomb`；都没有 → 原版音效
  （BGM 没有就不播）。权重要调就改包：这里把某条写小就少播、写 0 就不播。

=================== 两种用法 ===================

图形界面（默认，直接运行）：

    python tools/soundpack_maker.py

填「显示名」（必填）→ 选输出根目录 → 在某个键上「添加文件」（此时只登记源文件）
→ 逐个调权重 → 「审查配置」→ 玩家核对后手动点「生成音乐包」，才复制/转码并写出清单。
想做另一个包就点「新建包」（换一个新 id）。

命令行（无界面，适合批量制作；先预览再生成）：

    python tools/soundpack_maker.py --cli <源目录> --dry-run \
        [--name 显示名] [--author 作者] [--desc 说明] [--out 输出根目录]
    python tools/soundpack_maker.py --cli <源目录> \
        [包id] [--name 显示名] [--author 作者] [--desc 说明] [--out 输出根目录]

   * 音频键按**文件名**识别：`pass_1.mp3` → pass，`dan1.wav` → dan1，
     `bgm_playing.wav` → bgm_playing。键名后面要么结束，要么跟分隔符/数字
     （`pass_1.ogg` → pass、`dui1_2.ogg` → dui1；长键优先，故 `sidailiangdui`
     不会被 `sidaier` 抢走，`dan15` 也不会被 `dan1` 抢走）。
   * 包 id 一般不用写：默认随机生成；只有脚本要往**固定目录**里重写时才显式传，
     这时那个目录里如果是别人的包，程序会直接报错而不是覆盖它。
   * 省了 `--name` 就用源目录名当显示名（玩家在游戏里看到的就是它）。
   * 权重写在文件名里：`pass_1_w3.mp3` → pass 键、权重 3（默认 1）。
   * 源目录会**递归**扫描；已是 .ogg 的直接复制，其它格式转码成 Ogg Vorbis。
   * `--dry-run` 打印每个键的来源和权重、无法识别的文件，不创建目录或写入文件。
   * 重名绝不静默覆盖：名字被占且内容不同 → 改用 `_2`、`_3`……；内容相同 → 复用原文件。

转码依赖 PyAV（`pip install av`）。没有 PyAV 时仍能复制现成的 .ogg，只是不能转码。
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path
from zipfile import BadZipFile, ZipFile

try:
    import av
except ImportError:  # 只有转码才真的需要它，复制现成 .ogg 不需要
    av = None

try:  # 图形界面是可选路径：没装 tkinter 时命令行模式仍要能用
    import tkinter as tk
    from tkinter import filedialog, font as tkfont, messagebox, ttk

    TK_IMPORT_ERROR: Exception | None = None
except ImportError as _tk_error:  # pragma: no cover - 取决于运行环境
    tk = None
    TK_IMPORT_ERROR = _tk_error


# --------------------------------------------------------------------------- #
# 常量
# --------------------------------------------------------------------------- #

MANIFEST_NAME = "pack.json"
DEFAULT_OUT = "run/config/crafty_cards/soundpacks"
# 能作为输入的音频格式（源文件）；输出一律是 .ogg
AUDIO_EXTS = {".ogg", ".mp3", ".wav", ".flac", ".m4a", ".aac", ".opus"}
# 已经是 Ogg Vorbis 的直接复制，不必再过一遍编码器
COPY_EXTS = {".ogg"}
MIN_WEIGHT, MAX_WEIGHT = 1, 99
BIT_RATE = 128_000

# 与模组 CustomAudio.isAcceptableFileName 保持一致：^[A-Za-z0-9._-]+\.ogg$
LEGAL_NAME_RE = re.compile(r"[A-Za-z0-9._-]+")
PACK_ID_RE = re.compile(r"[A-Za-z0-9._-]+")

# 包 id 由本程序随机生成，玩家不填（理由见文件开头的「包 id 与显示名」）。
# 8 位小写十六进制：16^8 ≈ 4.3e9 种，同机放几个包时撞车概率在 1e-9 量级；
# 且不含 l / i / o 这类容易看错的字母——出错时人要能照着目录名念/抄。
PACK_ID_LENGTH = 8
PACK_ID_ALPHABET = "0123456789abcdef"
# 生成空闲 id 的重试上限：连撞多次说明运气极差，宁可报错也不继续换
PACK_ID_ATTEMPTS = 8

# 文件名里的权重标记：pass_1_w3.mp3 / pass.w3.ogg / pass-2-w4.flac
WEIGHT_MARKER_RE = re.compile(r"^(?P<stem>.+?)[._\- ]w(?P<weight>\d{1,2})$", re.IGNORECASE)

# 界面字体。两件事：
#   ① tk.Text 默认用 TkFixedFont（Windows 上是新宋体，等宽宋体系）——中文长说明用它最费眼，
#      所以正文（使用说明、日志）一律换成界面字体，这一条比单纯放大字号管用。
#   ② 这套界面的文字量大（61 行键表 + 长篇说明 + 日志），系统默认的 9pt 看着吃力，
#      基准改取 11pt；真·高 DPI 屏幕（缩放 > 100%）上再按比例放大。
UI_FONT_FAMILIES = ("Microsoft YaHei UI", "微软雅黑", "PingFang SC", "Noto Sans CJK SC",
                    "Source Han Sans SC", "WenQuanYi Micro Hei", "Hiragino Sans GB", "Segoe UI")
UI_BASE_SIZE = 11                   # 基准字号（96 DPI / 100% 缩放下的取值）
UI_FONT_MIN, UI_FONT_MAX = 10, 18   # 兜底区间，免得算出离谱的字号
BODY_FONT_EXTRA = 1                 # 正文（使用说明、日志）比 UI 再大一档


def _point_label(n: int) -> str:
    """点数编号 → 牌面：1=A、2~10 为牌面本身、11=J、12=Q、13=K、14=小王、15=大王。"""
    if 1 <= n <= 13:
        return {1: "A", 11: "J", 12: "Q", 13: "K"}.get(n, str(n))
    return {14: "小王", 15: "大王"}.get(n, str(n))


def _voice_keys() -> list[tuple[str, str]]:
    """生成 48 个牌型语音键（dan1..dan15、dui1..dui13、tuple1..tuple13 + 7 个牌型名）。"""
    keys = [(f"dan{i}", f"单张 {_point_label(i)}") for i in range(1, 16)]
    keys += [(f"dui{i}", f"对子 {_point_label(i)}") for i in range(1, 14)]
    keys += [(f"tuple{i}", f"三条 {_point_label(i)}") for i in range(1, 14)]
    keys += [
        ("sandaiyi", "三带一 / 三带二"),
        ("shunzi", "顺子"),
        ("liandui", "连对"),
        ("feiji", "飞机"),
        ("sidaier", "四带二（单）"),
        ("sidailiangdui", "四带二（对）"),
        ("wangzha", "王炸 / 火箭"),
    ]
    return keys


# 中文名必须与游戏侧 CustomAudio.label() 逐字一致（那边写着"界面与音乐包制作程序
# 共用一套说法"），且两两不同——BGM 那组的「BGM：」前缀就是"结算音效 vs 结算 BGM"
# 的区分依据，去掉前缀它们会和 win/lose 撞名（改版前的缺陷：列表里两行一模一样）。
EFFECT_KEYS: list[tuple[str, str]] = [
    ("deal", "发牌 / 洗牌"),
    ("bid", "叫分确认"),
    ("play", "出牌（通用）"),
    ("bomb", "炸弹 / 火箭"),
    ("pass", "过牌（不要）"),
    ("win", "结算胜利"),
    ("lose", "结算失败"),
]
BGM_KEYS: list[tuple[str, str]] = [
    ("bgm_waiting", "BGM：等待玩家"),
    ("bgm_playing", "BGM：对局进行中"),
    ("bgm_clutch", "BGM：有人只剩三张牌"),
    ("bgm_rocket", "BGM：王炸之后"),
    ("bgm_win", "BGM：结算胜利"),
    ("bgm_lose", "BGM：结算失败"),
]
VOICE_KEYS: list[tuple[str, str]] = _voice_keys()
# 界面按这个分组顺序展示，清单里的键也按这个顺序写，保证输出稳定
SECTIONS: list[tuple[str, list[tuple[str, str]]]] = [
    ("音效", EFFECT_KEYS),
    ("背景音乐", BGM_KEYS),
    ("牌型语音", VOICE_KEYS),
]
KEY_LABELS: dict[str, str] = {key: label for _, keys in SECTIONS for key, label in keys}

# 每个分组"什么时候响"（界面分组标题上直接写出来）
# 注意：这些文字进的是 tkinter 的 Label，markdown 不会渲染——写了星号只会原样显示出来，
# 所以界面上的提示文字一律用纯文本。
SECTION_HINTS: dict[str, str] = {
    "音效": "开局、叫分、出牌、过牌、结算各响一次（都在你本机播，别人的动作也听得到）",
    "背景音乐": "按牌局阶段循环播放；残局/王炸那两首没配会自动退回「对局进行中」（不会拿原版音乐凑数）",
    "牌型语音": "出牌时按牌型念出来；某个牌型没配就退回上面的「出牌（通用）」",
}

# 每个键更细的触发时机（显示在键名后面，省得玩家猜）
# 音效都是"响一次"、BGM 都是"循环"，所以两组各自写明——这是两类键唯一的区别，
# 不写清楚的话"结算胜利"与"BGM：结算胜利"在玩家眼里就是同一个东西。
# 这几条按 ClientDDZData.playTransitionSounds / DDZGameHud 的实际调用点写：
# 只有结算（win/lose）是"自己"的胜负，其余都是**谁做谁触发**（别人的动作你也听得到）。
_EFFECT_KEY_HINTS: dict[str, str] = {
    "deal": "开局发牌时响一次",
    "bid": "有人把叫分抬高时响一次（含定下地主那次）",
    "play": "有人出牌、且该牌型没有语音时响一次",
    "bomb": "打出炸弹时响一次（火箭走「王炸」语音，没配才落到它）",
    "pass": "有人过牌（说“不要”）时响一次",
    "win": "结算自己赢时响一次（一次性音效）",
    "lose": "结算自己输时响一次（一次性音效）",
}
_BGM_KEY_HINTS: dict[str, str] = {
    "bgm_waiting": "牌桌等人时循环（背景音乐）",
    "bgm_playing": "叫分、出牌阶段循环（背景音乐）",
    "bgm_clutch": "有人只剩三张以内的牌时循环（他没配就继续放「对局进行中」）",
    "bgm_rocket": "王炸打出来后循环，直到下一轮重新出牌（没配则依次退到残局/对局进行中）",
    "bgm_win": "结算自己赢时循环（背景音乐）",
    "bgm_lose": "结算自己输时循环（背景音乐）",
}
# 牌型语音统一是"打出某个牌型时响"
KEY_HINTS: dict[str, str] = {**_EFFECT_KEY_HINTS, **_BGM_KEY_HINTS}
KEY_HINTS.update({key: f"打出「{label}」时响" for key, label in VOICE_KEYS})
# 界面判断用：哪些键属于"牌型语音"（空键的后果不同，提示文字也不同）
VOICE_KEY_SET = {key for key, _ in VOICE_KEYS}

# 构建后要在游戏里做的事（日志与说明里都会写）
GAME_STEPS = "\n".join([
    "① 打开设置（主菜单 → Mods → Crafty Cards → Config，或输入 /craftycards sounds）",
    "② 进「音乐包」页点「扫描」→ 在列表里点你这个包的「选用」",
    "③ 点「保存并应用」（音频要重载一次才会被游戏加载）",
    "④ 想微调权重：回到本程序改（游戏内不再有逐键改权重/试听的入口）",
])

# 游戏侧与音频有关的旋钮（改游戏界面时同步这里；「使用说明」里原样展示）
GAME_PARAMS = "\n".join([
    "音乐包页：总开关（关掉后音效退回原版、BGM 不播、音乐包也不加载）、音量、",
    "  选用哪个包 / 重新扫描 / 各包的作者与覆盖情况 / 音乐包目录",
    "（BGM 声道与「压低原版背景音乐」没有界面入口：需要时改 config/crafty_cards/sounds.json，重启生效）",
])
KEY_ORDER: list[str] = list(KEY_LABELS)
# 长键优先匹配，避免 sidaier 抢先匹配 sidailiangdui、dan1 抢先匹配 dan15
_KEYS_BY_LENGTH: list[str] = sorted(KEY_LABELS, key=len, reverse=True)

assert len(VOICE_KEYS) == 48, "牌型语音应为 48 个"
# 与游戏侧 CustomAudio.allKeys() 对齐：13 个槽位（7 音效 + 6 BGM）+ 48 条牌型语音 = 61
assert len(KEY_LABELS) == 13 + 48, "音频键总数应与游戏侧一致（改了键要同步 CustomAudio）"
# 中文名两两不同：列表里出现两个一模一样的中文名，玩家就分不清哪个键是哪个。
# 这条断言是为 win/lose 与 bgm_win/bgm_lose 撞名（都叫"结算胜利"）加的。
_KEY_LABEL_LIST = list(KEY_LABELS.values())
assert len(set(_KEY_LABEL_LIST)) == len(_KEY_LABEL_LIST), (
    "音频键的中文名重复了：" + "、".join(sorted(
        {label for label in _KEY_LABEL_LIST if _KEY_LABEL_LIST.count(label) > 1})))


# --------------------------------------------------------------------------- #
# 纯逻辑：校验、名字压平、键识别、转码
# --------------------------------------------------------------------------- #


def validate_pack_id(pack_id: str) -> str:
    """校验包 id（同时是包目录名），不合法时抛 ValueError。"""
    pid = (pack_id or "").strip()
    if not pid:
        raise ValueError("包 id 不能为空")
    if pid in {".", ".."}:
        raise ValueError("包 id 不能是 '.' 或 '..'")
    if len(pid) > 64:
        raise ValueError("包 id 太长（最多 64 个字符）")
    if not PACK_ID_RE.fullmatch(pid):
        raise ValueError("只能包含字母、数字、点、下划线和短横线（不能有空格、斜杠、中文）")
    return pid


def generate_pack_id(rng: random.Random | None = None) -> str:
    """随机生成一个包 id（8 位小写十六进制）。"""
    pick = (rng or random).choice
    return "".join(pick(PACK_ID_ALPHABET) for _ in range(PACK_ID_LENGTH))


def manifest_name(pack_dir: Path) -> str | None:
    """读一个已有包目录的清单里声明的显示名；没有清单或读不出来返回 None。"""
    try:
        data = json.loads((Path(pack_dir) / MANIFEST_NAME).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    name = data.get("name") if isinstance(data, dict) else None
    return name.strip() if isinstance(name, str) and name.strip() else None


def read_existing_pack(source: Path, staging_dir: Path | None = None) -> tuple[dict[str, str], dict[str, list[dict]]]:
    """Read an existing pack directory or single-pack ZIP for GUI editing.

    ZIP audio is extracted only to a temporary staging directory, never to the
    configured output directory. The caller owns and cleans up that staging area.
    """
    source = Path(source)
    archive: ZipFile | None = None
    try:
        if source.is_dir():
            raw = (source / MANIFEST_NAME).read_bytes()
            base = ""
        elif source.is_file() and source.suffix.lower() == ".zip":
            if staging_dir is None:
                raise ValueError("导入 ZIP 需要临时暂存目录")
            archive = ZipFile(source)
            manifests = [name for name in archive.namelist()
                         if name == MANIFEST_NAME or name.endswith("/" + MANIFEST_NAME)]
            if len(manifests) != 1:
                raise ValueError("ZIP 必须只包含一个音乐包；三包合集请先解压，逐个选择包目录")
            base = manifests[0][:-len(MANIFEST_NAME)]
            if archive.getinfo(manifests[0]).file_size > 1024 * 1024:
                raise ValueError("pack.json 超过 1 MiB")
            raw = archive.read(manifests[0])
        else:
            raise ValueError("请选择含 pack.json 的包目录或单包 ZIP")

        manifest = json.loads(raw.decode("utf-8-sig"))
        if not isinstance(manifest, dict):
            raise ValueError("pack.json 必须是 JSON 对象")
        sounds = manifest.get("sounds")
        if sounds is None:
            sounds = {}
        if not isinstance(sounds, dict):
            raise ValueError("sounds 必须是音频键到文件列表的映射")
        mappings: dict[str, list[tuple[str, int]]] = {key: [] for key in KEY_ORDER}
        for key, entries in sounds.items():
            if key not in mappings:
                continue
            if not isinstance(entries, list):
                raise ValueError(f"音频键 {key} 的条目必须是列表")
            for entry in entries:
                if isinstance(entry, str):
                    filename, weight = entry, 1
                elif isinstance(entry, dict):
                    filename, weight = entry.get("file"), entry.get("weight", 1)
                else:
                    raise ValueError(f"音频键 {key} 含无效条目")
                if not isinstance(filename, str) or not is_acceptable_file_name(filename):
                    raise ValueError(f"音频键 {key} 的文件名不合法：{filename}")
                if type(weight) is not int or not MIN_WEIGHT <= weight <= MAX_WEIGHT:
                    raise ValueError(f"音频键 {key} 的权重不合法：{weight}")
                mappings[key].append((filename, weight))
        for section in ("soundEffects", "bgm"):
            legacy = manifest.get(section) or {}
            if not isinstance(legacy, dict):
                raise ValueError(f"{section} 必须是映射")
            for key, filename in legacy.items():
                if key in mappings and not mappings[key]:
                    if not isinstance(filename, str) or not is_acceptable_file_name(filename):
                        raise ValueError(f"音频键 {key} 的文件名不合法：{filename}")
                    mappings[key].append((filename, 1))
        if not any(mappings.values()):
            raise ValueError("包里没有可识别的音频映射")

        items: dict[str, list[dict]] = {key: [] for key in KEY_ORDER}
        copied: set[str] = set()
        total_bytes = 0
        for key in KEY_ORDER:
            for filename, weight in mappings[key]:
                if archive is None:
                    audio = source / filename
                    if not audio.is_file():
                        raise ValueError(f"包内缺少音频文件：{filename}")
                else:
                    member = base + filename
                    try:
                        info = archive.getinfo(member)
                    except KeyError:
                        raise ValueError(f"ZIP 内缺少音频文件：{member}") from None
                    if info.file_size > 64 * 1024 * 1024:
                        raise ValueError(f"单个音频文件超过 64 MiB：{filename}")
                    audio = Path(staging_dir) / filename
                    if filename not in copied:
                        total_bytes += info.file_size
                        if total_bytes > 512 * 1024 * 1024:
                            raise ValueError("音乐包总音频超过 512 MiB")
                        audio.parent.mkdir(parents=True, exist_ok=True)
                        audio.write_bytes(archive.read(info))
                        copied.add(filename)
                with audio.open("rb") as stream:
                    header = stream.read(4)
                if header != b"OggS":
                    raise ValueError(f"不是有效的 Ogg 文件：{filename}")
                items[key].append({"name": filename, "weight": weight,
                                   "src": str(audio), "dir": None})
        meta = {field: str(manifest.get(field) or "").strip()
                for field in ("name", "author", "description")}
        if not meta["name"]:
            meta["name"] = source.stem
        return meta, items
    except (BadZipFile, UnicodeError, json.JSONDecodeError) as e:
        raise ValueError(f"音乐包格式错误：{e}") from e
    finally:
        if archive is not None:
            archive.close()


def pick_free_pack_id(out_root: Path, log=print, rng: random.Random | None = None) -> str:
    """挑一个新的包 id：只认"目录不存在"的算空闲（残留目录也不碰）。"""
    out_root = Path(out_root)
    for _ in range(PACK_ID_ATTEMPTS):
        pid = generate_pack_id(rng)
        if not (out_root / pid).exists():
            return pid
        log(f"  包 id {pid} 已被占用，换一个")
    raise ValueError(f"连续 {PACK_ID_ATTEMPTS} 次都没生成出空闲的包 id，请重试")


def claim_pack_dir(
    out_root: Path,
    pack_id: str,
    name: str,
    owned: bool = False,
    log=print,
    rng: random.Random | None = None,
    strict: bool = False,
) -> tuple[str, Path]:
    """按当前 id 定下这次要写的包目录，返回 (包 id, 包目录)。

    同一个目录里只放得下一份 `pack.json`，静默覆盖就等于把那个包毁掉，所以先看住它：

      * 目录里没有清单（新建 / 只有残留文件）→ 可以用；
      * 清单写着**同一个显示名** → 当作同一个包在重新构建（改过显示名也一样，因为
        同一个 id 就是我们自己的包）→ 可以用；
      * `owned=True`（本次运行已经写过这个目录）→ 是自己的 → 可以用；
      * 清单写着**别的显示名** → 那是别人的包：默认**换一个新 id**（原包毫发无损，
        只是日志里说明一句）；`strict=True`（命令行里人自己指定的 id）则直接抛
        ValueError——人明确要往这个目录写，那就告诉他这里已经有别人的包，别猜。
    """
    out_root = Path(out_root)
    pid = validate_pack_id(pack_id)
    pack_dir = out_root / pid

    other = manifest_name(pack_dir)
    if other is None or other == name or owned:
        return pid, pack_dir

    if strict:
        raise ValueError(
            f"{pack_dir} 里已经有一个叫「{other}」的包，一个 id 只能属于一个包"
            f"（要覆盖它请先把那个包移走，或换一个 id）")
    free = pick_free_pack_id(out_root, log=log, rng=rng)
    log(f"  包 id {pid} 已被「{other}」占用，改用 {free}（原来的包不受影响）")
    return free, out_root / free


def is_acceptable_file_name(name: str) -> bool:
    """包内文件名是否合法：`[A-Za-z0-9._-]+.ogg`、不以点开头、不只是 ".ogg"。

    与模组 `CustomAudio.isAcceptableFileName` 同一套规则（防路径穿越 + 防 JSON 注入）。
    """
    if not name or not name.endswith(".ogg"):
        return False
    if len(name) <= 4 or name.startswith("."):
        return False
    return LEGAL_NAME_RE.fullmatch(name) is not None


def flatten_file_name(raw: str) -> str:
    """把任意名字压成合法的包内文件名：取 basename、小写、白名单字符、`.ogg` 结尾。

    例：`我的 语音(1).mp3` → `audio_1.ogg`；`Pass.mp3` → `pass.ogg`。
    压完为空则用 `audio`；超长会截断（文件名太长在部分系统上会失败）。
    """
    base = Path(str(raw)).name  # 只取文件名，顺带去掉 `../` 之类
    stem = base.rsplit(".", 1)[0] if "." in base else base
    chars = [c if (c.isascii() and (c.isalnum() or c in "._-")) else "_" for c in stem.lower()]
    stem = re.sub(r"_{2,}", "_", "".join(chars)).strip("._-")
    if not re.search(r"[a-z]", stem):  # 纯中文名会被压成 "1" 这种，加上前缀免得全长得一样
        stem = f"audio_{stem}" if stem else "audio"
    if len(stem) > 60:
        stem = stem[:60].strip("._-") or "audio"
    return stem + ".ogg"


def split_weight_marker(stem: str) -> tuple[str, int]:
    """从文件名主干里剥出权重标记 `_w3` / `.w3` / `-w3`，返回 (去掉标记的主干, 权重)。

    没有标记或数字越界时返回 (主干, 1)。
    """
    m = WEIGHT_MARKER_RE.match(stem)
    if not m:
        return stem, 1
    weight = int(m.group("weight"))
    if not MIN_WEIGHT <= weight <= MAX_WEIGHT:
        return stem, 1
    return m.group("stem"), weight


def preferred_file_name(raw_name: str) -> str:
    """源文件名 → 想要的包内文件名：已经合法就沿用，否则压平（并剥掉权重标记）。"""
    name = Path(str(raw_name)).name
    if is_acceptable_file_name(name):
        return name
    stem = Path(name).stem
    stem, _ = split_weight_marker(stem)
    return flatten_file_name(stem)


def match_key(stem: str) -> str | None:
    """文件名主干 → 音频键；识别不了返回 None。

    键名后面要么结束，要么跟分隔符（`_ - . `）或数字：`pass` / `pass_1` → pass。
    空格先归一成下划线，所以 `BGM WIN.wav` 也能认出 `bgm_win`。
    """
    s = re.sub(r"\s+", "_", (stem or "").strip().lower())
    if not s:
        return None
    for key in _KEYS_BY_LENGTH:
        if s == key:
            return key
        if s.startswith(key):
            rest = s[len(key)]
            if rest in "_-." or rest.isdigit():
                return key
    return None


def convert(src: Path, dst: Path, bit_rate: int = BIT_RATE) -> None:
    """转码为 Ogg Vorbis（Minecraft 唯一支持的音频格式）。

    三处环境上的坑，改这段代码前请先读：

    1. ffmpeg 的**原生 vorbis 编码器被标记为实验性**，必须显式放行——PyAV 只能用
       `codec_context.options = {"strict": "-2"}` 传（在 FLAC 等编码器上验证过这个
       选项确实生效，值 "normal" 会被拒、"-2" 能开）。
    2. **不要给编码器设 layout**。这台机器上的 PyAV 17.1 / libavcodec 62 里，把
       layout 设成 mono（或任何非默认值）会让 avcodec_open2 直接失败（报
       PermissionError/EINVAL，看不出所以然）。所以这里不碰 layout——编码器默认
       stereo，单声道源由重采样器升成双声道（对音效/语音无感知差异）。
    3. 写出时开 `fflags=+bitexact`（= AVFMT_FLAG_BITEXACT），让 ogg 复用器用固定的
       页序号而不是随机数。这样**同样的输入产出同样的字节**，重复构建时 `place()`
       才能判定"内容相同"而复用旧文件，不会每次重建都堆出 `_2`、`_3` 副本。
       （实测：不开这个标志时两次转码只有 24 个字节不同，全是页序号与随之变化的
       页校验和。）
    """
    with av.open(str(src)) as inp:
        stream = inp.streams.audio[0]
        with av.open(str(dst), "w", format="ogg", options={"fflags": "+bitexact"}) as out:
            enc = out.add_stream("vorbis", rate=stream.sample_rate)
            enc.codec_context.options = {"strict": "-2"}
            enc.format = "fltp"  # 原生 vorbis 编码器只吃浮点平面格式
            enc.bit_rate = bit_rate
            # 重采样到编码器要求的格式/声道/采样率，交给 ffmpeg 做格式转换
            resampler = av.AudioResampler(format=enc.format.name, layout=enc.layout, rate=enc.rate)
            for frame in inp.decode(stream):
                for resampled in resampler.resample(frame):
                    for packet in enc.encode(resampled):
                        out.mux(packet)
            for packet in enc.encode(None):
                out.mux(packet)


def _same_bytes(a: Path, b: Path) -> bool:
    """按块比较两个文件内容（大 BGM 也不整段读进内存）。"""
    try:
        if a.stat().st_size != b.stat().st_size:
            return False
        with a.open("rb") as fa, b.open("rb") as fb:
            while True:
                chunk_a, chunk_b = fa.read(65536), fb.read(65536)
                if chunk_a != chunk_b:
                    return False
                if not chunk_a:
                    return True
    except OSError:
        return False


def open_in_default_player(path: Path) -> None:
    """用系统默认播放器打开文件——这是本程序唯一的试听方式（刻意不内嵌播放器：
    听到的音质、音量、输出设备与平时一致，反复对比时不会有第二套音量设置掺进来）。"""
    target = str(path)
    if sys.platform.startswith("win"):
        os.startfile(target)  # type: ignore[attr-defined]  # noqa: S606 - 就是要在 Windows 上开播放器
    elif sys.platform == "darwin":
        subprocess.Popen(["open", target])
    else:
        subprocess.Popen(["xdg-open", target])


def _name_candidates(base: str) -> Iterator[str]:
    """候选包内文件名：`a.ogg`、`a_2.ogg`、`a_3.ogg`……（绝不覆盖同名异内容文件）。"""
    stem, dot, ext = base.rpartition(".")
    yield base
    for i in range(2, 1000):
        yield f"{stem}_{i}{dot}{ext}"


# --------------------------------------------------------------------------- #
# 写包：GUI 与命令行共用
# --------------------------------------------------------------------------- #


class PackWriter:
    """把音频文件放进包目录，并写出 pack.json。

    `log` 是可调用对象（命令行用 print、界面用日志面板），一切动作与警告都经它输出，
    这样两条入口共用同一套逻辑。
    """

    def __init__(self, pack_dir: Path, log=print, bit_rate: int = BIT_RATE) -> None:
        self.pack_dir = Path(pack_dir)
        self.log = log
        self.bit_rate = bit_rate
        # 归一化文件名（小写）→ 来源标识；同一次构建里防止两个来源抢同一个名字
        self._claimed: dict[str, str] = {}

    def ensure_dir(self) -> None:
        """创建包目录（父目录一并创建）。失败时抛 OSError。"""
        self.pack_dir.mkdir(parents=True, exist_ok=True)

    def place(self, src: Path, name: str | None = None) -> str:
        """把源文件放进包目录，返回最终文件名。

        已是 .ogg 直接复制，其它格式转码；非法的目标名会被压平。
        目标是**绝不静默覆盖**：候选名已存在且内容不同 → 试下一个名字（`_2`、`_3`…）；
        内容完全相同 → 复用已有文件（重复构建同一个包不会堆出一堆副本）。
        """
        src = Path(src)
        if not src.is_file():
            raise FileNotFoundError(f"文件不存在：{src}")
        if src.suffix.lower() not in AUDIO_EXTS:
            raise ValueError(f"不支持的音频格式：{src.suffix or '（无扩展名）'}")

        base = name or preferred_file_name(src.name)
        if not is_acceptable_file_name(base):
            base = flatten_file_name(base)
        identity = str(src.resolve()).lower()
        self.ensure_dir()

        tmp: Path | None = None  # 复制/转码的产物；多个候选名共用一份，收尾时删掉
        try:
            for cand in _name_candidates(base):
                ckey = cand.lower()
                target = self.pack_dir / cand
                if self._claimed.get(ckey) == identity:
                    return cand  # 同一个源文件重复添加 → 直接复用
                if target.exists() or ckey in self._claimed:
                    # 名字被占（本次已用，或磁盘上早就有）：内容相同才复用，否则换名字
                    if tmp is None:
                        tmp = self._materialize(src)
                    if target.exists() and _same_bytes(tmp, target):
                        self._claimed[ckey] = identity
                        self.log(f"  复用已有文件 {cand}（内容相同，未覆盖）")
                        return cand
                    continue
                if tmp is None:
                    tmp = self._materialize(src)
                os.replace(tmp, target)
                tmp = None
                self._claimed[ckey] = identity
                verb = "复制" if src.suffix.lower() in COPY_EXTS else "转码"
                self.log(f"  {verb} {src.name} → {cand}（{target.stat().st_size} 字节）")
                return cand
            raise RuntimeError(f"可用文件名耗尽（{base} 及其 _2…_999 都被占用）")
        finally:
            if tmp is not None:
                try:
                    tmp.unlink()
                except OSError:
                    pass

    def _materialize(self, src: Path) -> Path:
        """把源文件变成一份合法的 .ogg 临时文件（在包目录内，便于 os.replace 原子替换）。"""
        fd, raw = tempfile.mkstemp(prefix=".tmp-craftycards-", suffix=".ogg", dir=str(self.pack_dir))
        os.close(fd)
        tmp = Path(raw)
        try:
            if src.suffix.lower() in COPY_EXTS:
                shutil.copyfile(src, tmp)
            else:
                if av is None:
                    raise RuntimeError("转码需要 PyAV：pip install av")
                convert(src, tmp, self.bit_rate)
        except BaseException:
            try:
                tmp.unlink()
            except OSError:
                pass
            raise
        return tmp

    def write_manifest(
        self,
        *,
        name: str,
        author: str,
        description: str,
        sounds: dict[str, list[tuple[str, int]]],
    ) -> Path:
        """写出 pack.json（version 2）。

        `sounds`：音频键 → [(文件名, 权重)]；权重 1 写成裸字符串，其余写成对象；
        没有条目的键整条省略。字段顺序固定，方便 diff 与人工核对。
        """
        body: dict[str, list[object]] = {}
        for key in sounds:
            if key not in KEY_LABELS:
                self.log(f"  ! 未知音频键，已忽略：{key}")
        for key in KEY_ORDER:  # 固定顺序输出
            entries: list[object] = []
            for file_name, weight in sounds.get(key, []):
                w = max(MIN_WEIGHT, min(MAX_WEIGHT, int(weight)))
                entries.append(file_name if w == 1 else {"file": file_name, "weight": w})
            if entries:
                body[key] = entries

        manifest = {
            "version": 2,
            "name": name,
            "author": author,
            "description": description,
            "sounds": body,
        }
        path = self.pack_dir / MANIFEST_NAME
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return path


def scan_source_dir(source: Path, log=print) -> dict[str, list[tuple[Path, int]]]:
    """扫描源目录（递归）→ 音频键 → [(源文件, 权重)]，按文件名识别键。"""
    found: dict[str, list[tuple[Path, int]]] = {}
    skipped: list[str] = []
    for path in sorted(source.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in AUDIO_EXTS:
            continue
        stem, weight = split_weight_marker(path.stem)
        key = match_key(stem)
        if key is None:
            skipped.append(path.name)
            continue
        found.setdefault(key, []).append((path, weight))
    if skipped:
        log(f"忽略无法识别音频键的文件（文件名不是键名）：{', '.join(sorted(skipped))}")
    return found


# --------------------------------------------------------------------------- #
# 命令行模式
# --------------------------------------------------------------------------- #


def run_cli(args: argparse.Namespace) -> int:
    """`--cli` 模式：先可只读预览，再生成包目录与 pack.json。"""
    source = Path(args.source)
    if not source.is_dir():
        print(f"源目录不存在或不是目录：{source}")
        return 1

    # 显示名是玩家在游戏里认包的唯一依据，命令行没给 --name 就用源目录名顶上
    name = (args.name or "").strip()
    if not name:
        name = source.name.strip()
        if name:
            print(f"未指定 --name，用源目录名作为显示名：「{name}」")
    if not name:
        print("请用 --name 指定显示名（源目录名也不可用）")
        return 1

    out_root = Path(args.out)
    found = scan_source_dir(source)
    out_root_resolved = out_root.resolve()
    jobs: list[tuple[str, Path, int]] = []  # (音频键, 源文件, 权重)
    for key in KEY_ORDER:
        for src, weight in found.get(key, []):
            try:  # 别把上一次构建的产物（如果输出目录在源目录里）再吃进来
                if out_root_resolved in src.resolve().parents:
                    continue
            except OSError:
                pass
            jobs.append((key, src, weight))

    if not jobs:
        print("\n没有识别到任何音频键。文件名（去掉扩展名）必须是下列之一：")
        print(f"  {' '.join(KEY_ORDER)}")
        print("（可以带序号与权重：pass_1.ogg、pass_2_w3.mp3、dan1.wav、bgm_playing.wav）")
        return 1

    if getattr(args, "dry_run", False):
        print(f"\n预览：显示名「{name}」，输出根目录 {out_root}")
        print(f"识别到 {len({key for key, _, _ in jobs})} 个音频键、{len(jobs)} 个文件；没有写入文件。")
        for key, src, weight in jobs:
            print(f"  {key} · {KEY_LABELS[key]} ← {src}（权重 {weight}）")
        print("请核对无法识别文件、音频用途与来源授权；确认后去掉 --dry-run 生成。")
        return 0

    # 包 id 默认随机生成；命令行显式给了 id 就走严格模式：那个目录里有别人的包就报错
    try:
        if args.pack_id:
            pack_id, pack_dir = claim_pack_dir(out_root, args.pack_id, name, strict=True)
        else:
            pack_id = pick_free_pack_id(out_root)
            pack_dir = out_root / pack_id
    except ValueError as e:
        print(f"包 id 不可用：{e}")
        return 1
    print(f"包 id：{pack_id}（{'你指定' if args.pack_id else '自动生成'}）→ {pack_dir}")

    writer = PackWriter(pack_dir, log=print)
    try:
        writer.ensure_dir()
    except OSError as e:
        print(f"无法创建包目录 {pack_dir}：{e}")
        return 1

    sounds: dict[str, list[tuple[str, int]]] = {}
    failed: list[str] = []
    for key, src, weight in jobs:
        try:
            file_name = writer.place(src)
        except Exception as e:  # 单个文件失败不影响其它文件
            failed.append(src.name)
            print(f"  ! {src.name} 处理失败，已跳过：{e}")
            continue
        sounds.setdefault(key, []).append((file_name, weight))

    if failed:
        print(f"\n有 {len(failed)} 个文件处理失败：{', '.join(failed)}")
    if not sounds:
        print("\n所有文件都处理失败了，没有可用的条目。")
        return 1

    manifest_path = writer.write_manifest(
        name=name,
        author=args.author,
        description=args.desc,
        sounds=sounds,
    )

    keys_used = sum(1 for k in KEY_ORDER if sounds.get(k))
    entries = sum(len(v) for v in sounds.values())
    print(f"\n音乐包已生成：{pack_dir}")
    print(f"  包 id：{pack_id}（就是目录名，由程序生成，不用记）")
    print(f"  显示名：{name}（游戏里的音乐包列表显示这个）")
    print(f"  用了 {keys_used} 个音频键、{entries} 个文件")
    print(f"  清单：{manifest_path}")
    print("\n--- pack.json ---")
    print(manifest_path.read_text(encoding="utf-8").rstrip())
    print("\n在游戏的「配置 → 音乐包」里选用它（列表里按显示名找）。")
    return 0


# --------------------------------------------------------------------------- #
# 图形界面
# --------------------------------------------------------------------------- #


def default_out_root() -> str:
    """默认输出根目录：脚本在 `tools/` 下，优先用仓库里的 `run/config/...`。"""
    guess = Path(__file__).resolve().parent.parent / "run" / "config" / "crafty_cards" / "soundpacks"
    if guess.parent.is_dir():  # run/config/crafty_cards 已存在（开发环境）
        return str(guess)
    return DEFAULT_OUT


class SoundPackMakerApp:
    """图形界面。tkinter 在构造时才真正用到（类定义本身不需要 Tk）。"""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Crafty Cards 音乐包制作程序")
        # 字体要在建控件之前定好（控件是按当前字号布局的）
        self._apply_fonts()
        # 窗口开大一点，并且不超过屏幕（有些显示器只有 1366×768）
        width = min(1160, self.root.winfo_screenwidth() - 80)
        height = min(900, self.root.winfo_screenheight() - 120)
        self.root.geometry(f"{width}x{height}")
        self.root.minsize(min(980, width), min(680, height))

        # 包 id 由程序生成（玩家不填）：它是目录名，也是游戏里区分两个包的唯一依据。
        # 玩家填的是「显示名」与「作者」——那才是他在游戏里看到的东西。
        self.pack_id = tk.StringVar(value=pick_free_pack_id(Path(default_out_root()), log=lambda *_: None))
        self.pack_name = tk.StringVar()
        self.pack_author = tk.StringVar()
        self.pack_desc = tk.StringVar()
        self.out_root = tk.StringVar(value=default_out_root())
        # 本次运行已经成功写过哪个包目录（写过的就是自己的，重新构建不再当成"别人的包"）
        self._built_dir: Path | None = None
        self._import_tempdir: tempfile.TemporaryDirectory | None = None

        # 音频键 → [{"name": 包内文件名, "weight": 权重, "src": 源文件, "dir": 已放入的目录}]
        self.items: dict[str, list[dict]] = {key: [] for key in KEY_ORDER}
        self._weight_vars: dict[int, tk.StringVar] = {}  # id(item) → 权重输入框
        self._file_containers: dict[str, ttk.Frame] = {}
        self._status_labels: dict[str, ttk.Label] = {}
        # 键列表：行只建一次（_row_widgets / _group_headers），过滤只做显示与隐藏
        self._list_inner: ttk.Frame | None = None
        self._row_widgets: dict[str, ttk.Frame] = {}
        self._group_headers: list[dict] = []
        self._grid_row = 0                  # 下一个可用的 grid 行号（行位置一次定死）
        self._build_queue: list[tuple] = []
        self._rows_ready = False
        self._filter_job: str | None = None  # 搜索防抖的定时器 id
        self.only_filled = tk.BooleanVar(value=False)
        self.filter_text = tk.StringVar()

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self._on_meta_changed()
        self._refresh_all()
        self.log("这个程序把音频整理成「音乐包」——游戏里的音效与 BGM 全部来自音乐包。")
        self.log("四步：① 填包信息 → ② 添加源文件并设权重 → ③ 审查配置与试听 → ④ 玩家手动点「生成音乐包」。")
        self.log("包 id 由程序自动生成（8 位随机，就是包目录名）：玩家不用填，游戏里也看不到它——"
                 "你和别人各填各的显示名，包永远不会互相覆盖。")
        self.log("「权重」= 同一键下多个文件的相对概率（3 和 1 就是 3:1）。同一个键加多个文件就会随机播，听起来不重复。")
        self.log("没添加文件的键不会有声音：音效退回原版、BGM 不播、牌型语音退回「出牌（通用）」。")
        self.log("添加文件只登记映射，不写输出目录；点击审查页的「生成音乐包」后才复制或转码。")
        self.log("非 .ogg 会被自动转码成 Ogg Vorbis；包内文件名会自动清洗，同名不同内容不会覆盖。")
        self.log("构建完成后进游戏：设置 → 音乐包 → 扫描 → 选用（按显示名找）→ 保存并应用。")
        self.log("游戏里可调总开关、共用音量和选用哪个包；BGM 声道与压低原版音乐需手改 sounds.json。")
        self.log("再做另一个包点「新建包」：会清空当前内容并换一个新 id，已构建好的包不受影响。")
        self.log("点某个文件的「试听」会用系统默认播放器打开它（本程序不内嵌播放器）。")

        # 启动就把使用说明弹出来（置顶）：第一眼该看到"这程序怎么用"，
        # 而不是对着 61 个音频键猜。关掉之后右上角按钮随时能再打开。
        self._open_help_on_start()

    # ---------------- 界面搭建 ----------------

    def _apply_fonts(self) -> None:
        """统一界面字体与字号（必须在建控件之前调用）。

        ① **正文换成界面字体**：`tk.Text` 默认用 `TkFixedFont`——Windows 上是新宋体，等宽宋体
           系。中文长说明用它非常费眼；换成界面字体（雅黑/苹方/思源…）比放大字号管用得多。
        ② **字号取一个舒服的基准**：系统默认 9pt 是这个界面的老尺寸，而这里文字量很大
           （61 行键表 + 长篇说明 + 日志），基准改成 `UI_BASE_SIZE`。真·高 DPI 屏幕上再按
           DPI 比例放大：`tk scaling` 是"像素/点"，96 DPI（100% 缩放）时等于 96/72 ≈ 1.333，
           所以除以它才是"相对 100% 的倍数"——直接拿 scaling 当倍数会平白放大 33%。

        ttk 的按钮/标签/输入框/勾选框/分组框的字体都查 `TkDefaultFont`（实测 `style.lookup`
        返回的都是它），所以改命名字体就够了，不必逐个控件设 `font=`。
        """
        family = next((f for f in UI_FONT_FAMILIES if f in set(tkfont.families())), None)
        base = tkfont.nametofont("TkDefaultFont")
        dpi_factor = float(self.root.tk.call("tk", "scaling")) / (96.0 / 72.0)
        size = int(round(UI_BASE_SIZE * max(1.0, dpi_factor)))
        size = max(UI_FONT_MIN, min(UI_FONT_MAX, size))
        actual_family = family or str(base.actual("family"))

        for name in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont", "TkIconFont"):
            named = tkfont.nametofont(name)
            named.configure(size=size)
            if family:
                named.configure(family=family)

        self._body_font = tkfont.Font(family=actual_family, size=size + BODY_FONT_EXTRA)
        # 键列表挤着 61 行，用 UI 字号加粗即可；使用说明的标题跟着正文字号走
        self._bold = tkfont.Font(family=actual_family, size=size, weight="bold")
        self._body_bold = tkfont.Font(family=actual_family, size=size + BODY_FONT_EXTRA, weight="bold")

    def _build_ui(self) -> None:
        root = self.root
        root.columnconfigure(0, weight=1)
        # 只有"音频键"那一行吃多余的高度（行号见下面各 grid 的 row 参数）
        root.rowconfigure(2, weight=1)

        # 「使用说明」放在窗口右上角：它是个弹窗（启动时会自动弹一次），界面上只留这个入口。
        ttk.Button(root, text="使用说明", command=self._show_help).grid(
            row=0, column=0, sticky="e", padx=8, pady=(8, 0))

        meta = ttk.LabelFrame(root, text="包信息", padding=8)
        meta.grid(row=1, column=0, sticky="ew", padx=8, pady=(4, 4))
        meta.columnconfigure(1, weight=1)
        meta.columnconfigure(3, weight=1)

        # 玩家填的是「显示名」与「作者」——游戏里的音乐包列表显示的就是这两样
        ttk.Label(meta, text="显示名（必填）").grid(row=0, column=0, sticky="w")
        ttk.Entry(meta, textvariable=self.pack_name).grid(row=0, column=1, sticky="ew", padx=(4, 12))
        ttk.Label(meta, text="作者").grid(row=0, column=2, sticky="w")
        ttk.Entry(meta, textvariable=self.pack_author).grid(row=0, column=3, sticky="ew", padx=(4, 0))

        ttk.Label(meta, text="说明").grid(row=1, column=0, sticky="w", pady=(4, 0))
        ttk.Entry(meta, textvariable=self.pack_desc).grid(
            row=1, column=1, columnspan=3, sticky="ew", padx=(4, 0), pady=(4, 0))

        ttk.Label(meta, text="输出根目录").grid(row=2, column=0, sticky="w", pady=(6, 0))
        ttk.Entry(meta, textvariable=self.out_root).grid(
            row=2, column=1, columnspan=2, sticky="ew", padx=(4, 4), pady=(6, 0))
        ttk.Button(meta, text="选择…", command=self._pick_out_root).grid(
            row=2, column=3, sticky="w", pady=(6, 0))

        # 包 id 不给填（见文件开头的「包 id 与显示名」），界面上只把它显示在目标目录里：
        # 包信息框不放解释文字——"显示名（必填）"这个标签已经说清了要求，理由在「使用说明」里。
        self.target_label = ttk.Label(meta, text="")
        self.target_label.grid(row=3, column=0, columnspan=4, sticky="w", pady=(6, 0))

        bar = ttk.Frame(meta)
        bar.grid(row=4, column=0, columnspan=4, sticky="ew", pady=(8, 0))
        ttk.Button(bar, text="审查配置…", command=self._show_review).pack(side="left")
        ttk.Button(bar, text="导入包目录…", command=lambda: self._on_import(False)).pack(side="left", padx=(8, 0))
        ttk.Button(bar, text="导入 ZIP…", command=lambda: self._on_import(True)).pack(side="left", padx=(8, 0))
        ttk.Button(bar, text="新建包", command=self._on_new_pack).pack(side="left", padx=(8, 0))
        ttk.Button(bar, text="清空已选文件", command=self._on_clear_all).pack(side="left", padx=(8, 0))

        # 用法不再常驻在界面上：启动时会把「使用说明」弹出来（右上角那个按钮随时能再打开），
        # 所以这一整条"怎么用"的文字条已经没必要占地方。

        keys_box = ttk.LabelFrame(
            root,
            text="音频键（音效 7 + 背景音乐 6 + 牌型语音 48）——只把你添加了文件的键写进清单",
            padding=6,
        )
        keys_box.grid(row=2, column=0, sticky="nsew", padx=8, pady=4)
        keys_box.columnconfigure(0, weight=1)
        keys_box.rowconfigure(2, weight=1)

        # 第一行：权重是什么（最容易被误解的一处）+ 过滤条
        head_bar = ttk.Frame(keys_box)
        head_bar.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 2))
        head_bar.columnconfigure(0, weight=1)
        ttk.Label(head_bar, justify="left", foreground="#444444", text=(
            "每个文件后面的数字是「权重」= 相对概率：同一键下多个文件按权重随机播（3 和 1 就是 3:1）。"
        )).grid(row=0, column=0, sticky="w")
        ttk.Checkbutton(head_bar, text="只看已配置的键", variable=self.only_filled,
                        command=self._apply_filter).grid(row=0, column=1, sticky="e", padx=(8, 0))
        ttk.Label(head_bar, text="搜索").grid(row=0, column=2, sticky="e", padx=(12, 2))
        search = ttk.Entry(head_bar, textvariable=self.filter_text, width=16)
        search.grid(row=0, column=3, sticky="e")
        search.bind("<KeyRelease>", lambda e: self._schedule_filter())

        # 第二行：没添加文件的键会怎样（一次说清，就不用在每个键下面重复写了）
        ttk.Label(keys_box, justify="left", foreground="#666666", text=(
            "没添加文件的键不会有声音 —— 音效退回原版音效、BGM 不播（不会拿原版音乐凑数）、"
            "牌型语音退回「出牌（通用）」。每种键「什么时候响」写在键名右边。"
        )).grid(row=1, column=0, columnspan=2, sticky="w", pady=(0, 4))

        canvas = tk.Canvas(keys_box, highlightthickness=0, borderwidth=0)
        canvas.grid(row=1, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(keys_box, orient="vertical", command=canvas.yview)
        scroll.grid(row=1, column=1, sticky="ns")
        canvas.configure(yscrollcommand=scroll.set)

        self._canvas = canvas
        self._list_inner = ttk.Frame(canvas)
        self._list_inner.columnconfigure(0, weight=1)     # 行用 grid，要让它横向铺满
        window = canvas.create_window((0, 0), window=self._list_inner, anchor="nw")
        self._list_inner.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(window, width=e.width))
        # 滚轮只在鼠标位于列表上时接管，避免影响别处
        canvas.bind("<Enter>", lambda e: self._bind_wheel(canvas))
        canvas.bind("<Leave>", lambda e: self._unbind_wheel(canvas))

        # 列表最下面的两条提示（过滤掉多少键 / 一个都不剩）：先建出来，等行建完再摆到末尾
        self._filter_note = ttk.Label(self._list_inner, foreground="#888888")
        self._empty_note = ttk.Label(self._list_inner, foreground="#888888",
                                     text="没有符合条件的键（清掉搜索词或取消勾选试试）")

        # 行**不在建界面时一次建完**：61 行 + 3 个分组头 = 520 多个控件、约 0.75 秒，
        # 一次做完就是"开程序先白等一下"。改成窗口先显示，再分块填行（见 _build_rows_step）。
        self.root.after(1, self._build_rows_step)

        log_box = ttk.LabelFrame(root, text="日志（每一步做了什么、为什么被跳过，都写在这里）", padding=6)
        log_box.grid(row=3, column=0, sticky="ew", padx=8, pady=(4, 8))
        log_box.columnconfigure(0, weight=1)
        # 显式给正文字体：tk.Text 默认是 TkFixedFont（新宋体），中文日志用它很难读
        self._log_text = tk.Text(log_box, height=11, wrap="word", state="disabled",
                                 font=self._body_font, spacing3=2)
        self._log_text.grid(row=0, column=0, sticky="ew")
        log_scroll = ttk.Scrollbar(log_box, orient="vertical", command=self._log_text.yview)
        log_scroll.grid(row=0, column=1, sticky="ns")
        self._log_text.configure(yscrollcommand=log_scroll.set)
        for tag, color in (("err", "#b00020"), ("warn", "#a05a00"), ("ok", "#1a7f37")):
            self._log_text.tag_configure(tag, foreground=color)

        self.pack_id.trace_add("write", self._on_meta_changed)
        self.out_root.trace_add("write", self._on_meta_changed)

    # 各列的固定宽度：键名 / 中文名 / 状态 —— 定宽才能让 61 行纵向对齐；
    # 不定宽的话每行"什么时候响"的起点都不一样，整页看着就是一片乱
    COL_KEY = 15
    COL_LABEL = 17
    COL_STATUS = 9

    # 键列表的行**建一次就不再重建**（见 _build_rows_step / _apply_filter）：
    # 一次全量重建要建 520 多个控件、约 0.75 秒（实测：建行 550ms + 排版 420ms），
    # 而搜索框每敲一个键都会触发它。所以行用 grid 摆好固定位置，过滤只做
    # grid()/grid_remove() —— 隐藏过的行会记住自己的位置，显示出来顺序不会乱。
    LIST_CHUNK = 10      # 首屏分块建行：每个 tick 建几行
    FILTER_DELAY_MS = 200   # 搜索防抖：停手 200ms 再过滤

    def _build_rows_step(self) -> None:
        """分块把键列表建起来（窗口先显示，行随后自己长出来）。

        一次建完要 0.75 秒，表现为"开程序先白等一下"；分块之后窗口立刻可见，
        列表在约一秒内逐段出现，中途也不卡。
        """
        if not self._build_queue:
            # 顺序：分组头 → 该组的各个键 → 下一组的分组头……（分组头要挨着它的行）
            self._build_queue = []
            for title, keypairs in SECTIONS:
                self._build_queue.append(("group", (title, keypairs)))
                self._build_queue.extend(("key", (key, label)) for key, label in keypairs)

        budget = self.LIST_CHUNK
        while self._build_queue and budget > 0:
            kind, payload = self._build_queue.pop(0)
            if kind == "group":
                self._make_group_header(*payload)
            else:
                self._make_key_row(self._list_inner, *payload)
            budget -= 1

        if self._build_queue:
            self.root.after(1, self._build_rows_step)
            return
        # 行都摆好了，两条提示跟在末尾（grid 行号有上限，所以用当前计数而不是一个大常数）
        self._filter_note.grid(row=self._grid_row, column=0, sticky="w", pady=(8, 0))
        self._empty_note.grid(row=self._grid_row + 1, column=0, sticky="w", pady=(8, 0))
        self._filter_note.grid_remove()
        self._empty_note.grid_remove()
        self._rows_ready = True
        self._refresh_all()      # 建行期间可能已经加过文件，这里补上文件行
        self._apply_filter()

    def _make_group_header(self, title: str, keypairs: list[tuple[str, str]]) -> None:
        """分组标题行：标题（含总数）+ 已配置计数 + 这一组"什么时候响"。"""
        head = ttk.Frame(self._list_inner)
        head.grid(row=self._grid_row, column=0, sticky="ew", pady=(10, 2))
        self._grid_row += 1
        ttk.Label(head, text=f"{title}（{len(keypairs)}）", font=self._bold).pack(side="left")
        filled = ttk.Label(head, text="", foreground="#1a7f37")
        filled.pack(side="left")
        ttk.Label(head, text=f"　{SECTION_HINTS.get(title, '')}",
                  foreground="#888888").pack(side="left")
        self._group_headers.append(
            {"frame": head, "filled": filled, "keys": [key for key, _ in keypairs]})

    def _schedule_filter(self) -> None:
        """把过滤推迟到"停手"之后再跑：每敲一个键就重建一次列表会明显卡顿。"""
        if self._filter_job is not None:
            self.root.after_cancel(self._filter_job)
        self._filter_job = self.root.after(self.FILTER_DELAY_MS, self._apply_filter)

    def _apply_filter(self) -> None:
        """按当前过滤条件显示/隐藏已经建好的行（**不重建控件**）。

        重建一次要 0.75 秒（520 多个控件），而显示/隐藏只是布局，快得多。
        """
        self._filter_job = None
        if self._list_inner is None or not self._rows_ready:
            return          # 行还没建完；建完时 _build_rows_step 会自己套用一次
        # 过滤会改变内容高度、把滚动位置顶回顶部，先记下来，结束再放回去
        first_visible = self._canvas.yview()[0] if self._canvas is not None else 0.0
        keyword = self.filter_text.get().strip().lower()
        only_filled = bool(self.only_filled.get())
        hidden_by_filter = 0
        shown = 0

        for group in self._group_headers:
            visible = []
            for key in group["keys"]:
                row = self._row_widgets.get(key)
                if row is None:
                    continue
                if only_filled and not self.items[key]:
                    row.grid_remove()
                    continue
                if keyword and keyword not in key.lower() and keyword not in KEY_LABELS[key].lower() \
                        and keyword not in KEY_HINTS.get(key, "").lower():
                    row.grid_remove()
                    hidden_by_filter += 1
                    continue
                row.grid()       # 不带参数 = 恢复建行时的位置与选项
                visible.append(key)
                shown += 1
            group["filled"].configure(
                text=f"　已配置 {sum(1 for key in visible if self.items[key])}")
            if visible:
                group["frame"].grid()
            else:
                group["frame"].grid_remove()

        if hidden_by_filter:
            self._filter_note.configure(text=f"（搜索过滤掉了 {hidden_by_filter} 个键）")
            self._filter_note.grid()
        else:
            self._filter_note.grid_remove()
        if shown:
            self._empty_note.grid_remove()
        else:
            self._empty_note.grid()

        self._list_inner.update_idletasks()
        if self._canvas is not None and first_visible > 0:
            self._canvas.yview_moveto(first_visible)

    def _make_key_row(self, parent: ttk.Frame, key: str, label: str) -> None:
        """一个键一行：键名 / 中文名 / 什么时候响 / 状态 / 添加文件；有文件时下面再列文件行。"""
        row = ttk.Frame(parent)
        # 用 grid 而不是 pack：grid_remove() 能把行藏起来、再 grid() 原样放回，
        # 于是过滤不需要重建控件（位置由 row 索引固定，隐藏不会打乱顺序）
        row.grid(row=self._grid_row, column=0, sticky="ew", pady=1)
        self._grid_row += 1
        self._row_widgets[key] = row

        head = ttk.Frame(row)
        head.pack(fill="x")
        ttk.Button(head, text="添加文件", command=lambda k=key: self._on_add(k)).pack(side="right")
        status = ttk.Label(head, text="", width=self.COL_STATUS, anchor="e")
        status.pack(side="right", padx=(8, 10))
        ttk.Label(head, text=key, font=self._bold, width=self.COL_KEY, anchor="w").pack(side="left")
        ttk.Label(head, text=label, width=self.COL_LABEL, anchor="w").pack(side="left")
        # 这个键"什么时候响"——玩家最需要知道的一件事，放在固定列里
        ttk.Label(head, text=KEY_HINTS.get(key, ""), foreground="#777777").pack(side="left")

        files = ttk.Frame(row)
        # 文件行作为该键的"子项"缩进显示（缩进量与键名列无关，太大会显得和上一行脱节）
        files.pack(fill="x", padx=(26, 0))
        self._file_containers[key] = files
        self._status_labels[key] = status

    # ---------------- 滚轮 ----------------

    def _bind_wheel(self, canvas: tk.Canvas) -> None:
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(-1 * (e.delta // 120), "units"))
        canvas.bind_all("<Button-4>", lambda e: canvas.yview_scroll(-1, "units"))
        canvas.bind_all("<Button-5>", lambda e: canvas.yview_scroll(1, "units"))

    def _unbind_wheel(self, canvas: tk.Canvas) -> None:
        for seq in ("<MouseWheel>", "<Button-4>", "<Button-5>"):
            canvas.unbind_all(seq)

    # ---------------- 日志 ----------------

    def log(self, message: str, level: str = "info") -> None:
        """往日志面板追加一行；level: info / ok / warn / err。任何异常都不该打断主流程。"""
        try:
            text = self._log_text
            text.configure(state="normal")
            text.insert("end", f"{message}\n", level)
            text.see("end")
            text.configure(state="disabled")
            self.root.update_idletasks()
        except Exception:
            print(message)

    # ---------------- 包信息 ----------------

    def _out_root_path(self) -> Path:
        """当前设置的输出根目录（留空就用默认值）。"""
        return Path(self.out_root.get().strip() or DEFAULT_OUT)

    def target_dir(self) -> Path:
        """当前设置解析出的包目录（包 id 由程序生成，这里只兜一道合法性）。"""
        return self._out_root_path() / validate_pack_id(self.pack_id.get())

    def _on_meta_changed(self, *_args: object) -> None:
        try:
            target = self.target_dir()
        except ValueError as e:
            self.target_label.configure(text=f"目标目录：（包 id 不合法：{e}）", foreground="#b00020")
            return
        self.target_label.configure(text=f"目标目录：{target}", foreground="#333333")

    def _pick_out_root(self) -> None:
        try:
            chosen = filedialog.askdirectory(title="选择输出根目录（包目录会建在它下面）")
        except Exception as e:
            self.log(f"打开目录选择框失败：{e}", "err")
            return
        if chosen:
            self.out_root.set(chosen)
            self.log(f"输出根目录：{chosen}")

    def _on_new_pack(self) -> None:
        """开始做另一个包：清空当前内容与包信息，并换一个新的包 id。"""
        if any(self.items.values()) and not messagebox.askyesno(
                "新建包",
                "清空当前已选的文件与「包信息」，并换一个新的包 id？\n\n"
                "已经构建好的包在磁盘上，不会被动到。"):
            return
        self._commit_all_weights()
        for key in KEY_ORDER:
            self.items[key].clear()
        self.pack_name.set("")
        self.pack_author.set("")
        self.pack_desc.set("")
        self._built_dir = None
        if self._import_tempdir is not None:
            self._import_tempdir.cleanup()
            self._import_tempdir = None
        self.pack_id.set(pick_free_pack_id(self._out_root_path(), log=self.log))
        self._refresh_all()          # 文件都清空了：所有行的文件列表都要重画
        self._apply_filter()         # 顺带更新分组计数（"只看已配置的键"勾着时会全隐掉）
        self.log(f"已新建一个包（id {self.pack_id.get()}）：填「显示名」，再添加文件。", "ok")

    def _on_import(self, from_zip: bool) -> None:
        """Import a pack into editable GUI state without writing output files."""
        try:
            chosen = (filedialog.askopenfilename(
                title="选择单个音乐包 ZIP", filetypes=[("ZIP 音乐包", "*.zip")])
                if from_zip else filedialog.askdirectory(title="选择含 pack.json 的音乐包目录"))
        except Exception as e:
            self.log(f"打开导入选择框失败：{e}", "err")
            return
        if not chosen:
            return
        if any(self.items.values()) and not messagebox.askyesno(
                "导入音乐包", "导入会替换当前界面的包信息和已选文件；磁盘上的包不会改变。继续吗？"):
            return
        temporary = tempfile.TemporaryDirectory(prefix="crafty-cards-import-") if from_zip else None
        try:
            meta, items = read_existing_pack(Path(chosen), Path(temporary.name) if temporary else None)
            new_id = pick_free_pack_id(self._out_root_path(), log=self.log)
        except (OSError, ValueError, RuntimeError) as e:
            if temporary is not None:
                temporary.cleanup()
            self.log(f"导入音乐包失败：{e}", "err")
            messagebox.showerror("导入音乐包", str(e))
            return
        old_temporary = self._import_tempdir
        self._weight_vars.clear()
        self.items = items
        self.pack_name.set(meta["name"])
        self.pack_author.set(meta["author"])
        self.pack_desc.set(meta["description"])
        self.pack_id.set(new_id)
        self._built_dir = None
        self._import_tempdir = temporary
        self._refresh_all()
        self._apply_filter()
        if old_temporary is not None:
            old_temporary.cleanup()
        count = sum(len(entries) for entries in items.values())
        self.log(f"已导入 {meta['name']}：{count} 条映射。原包不变；请审查后手动生成新包。", "ok")

    def _on_close(self) -> None:
        if self._import_tempdir is not None:
            self._import_tempdir.cleanup()
            self._import_tempdir = None
        self.root.destroy()

    def _require_target(self) -> Path | None:
        """取出目标目录；不合法就记日志并返回 None（界面永不因此崩溃）。"""
        try:
            return self.target_dir()
        except ValueError as e:
            self.log(f"包 id 不合法：{e}", "err")
            return None

    # ---------------- 键上的文件操作 ----------------

    def _refresh_key(self, key: str) -> None:
        frame = self._file_containers.get(key)
        if frame is None:          # 这个键被过滤条件隐藏了
            return
        for child in frame.winfo_children():
            child.destroy()
        for item in self.items[key]:
            self._weight_vars.pop(id(item), None)
            self._make_file_row(frame, key, item)
        used = len(self.items[key])
        status = self._status_labels.get(key)
        if status is not None:
            # 空态只用一个词表示：原来每个键盘下面还挂一行灰字解释，48 个语音键就是 48 行噪音；
            # "没添加文件的键会怎样"已经在列表上方统一说明了
            status.configure(text=f"已配置 {used}" if used else "未添加",
                             foreground="#1a7f37" if used else "#999999")

    def _refresh_all(self) -> None:
        for key in KEY_ORDER:
            self._refresh_key(key)

    def _make_file_row(self, parent: ttk.Frame, key: str, item: dict) -> None:
        row = ttk.Frame(parent)
        row.pack(fill="x")
        ttk.Label(row, text="•").pack(side="left")
        ttk.Label(row, text=item["name"], width=30, anchor="w").pack(side="left")

        # 权重：标签 + 可直接输入的数字框 + - / +（不用 Spinbox：它自带的上/下小箭头
        # 与 - / + 是同一件事，摆在一起既挤又容易点错，所以只留 - / +）
        ttk.Label(row, text="权重", foreground="#666666").pack(side="left", padx=(8, 2))
        var = tk.StringVar(value=str(item["weight"]))
        entry = ttk.Entry(row, width=4, justify="center", textvariable=var)
        entry.pack(side="left")
        entry.bind("<FocusOut>", lambda e, i=item, v=var: self._commit_weight(i, v))
        entry.bind("<Return>", lambda e, i=item, v=var: self._commit_weight(i, v))
        self._weight_vars[id(item)] = var

        ttk.Button(row, text="-", width=2, command=lambda i=item: self._bump_weight(i, -1)).pack(
            side="left", padx=(4, 0))
        ttk.Button(row, text="+", width=2, command=lambda i=item: self._bump_weight(i, 1)).pack(
            side="left", padx=(2, 0))
        ttk.Button(row, text="试听", command=lambda i=item: self._preview(i)).pack(side="left", padx=(8, 0))
        ttk.Button(row, text="移除", command=lambda k=key, i=item: self._remove(k, i)).pack(
            side="left", padx=(4, 0))

        src = item.get("src")
        if src and Path(src).name != item["name"]:
            ttk.Label(row, text=f"← {Path(src).name}").pack(side="left", padx=(8, 0))

    def _commit_weight(self, item: dict, var: tk.StringVar) -> None:
        """把输入框的值收进状态；非法值退回原值并提示。"""
        text = str(var.get()).strip()
        try:
            weight = int(text)
        except ValueError:
            weight = int(item["weight"])
            self.log(f"权重必须是 {MIN_WEIGHT}~{MAX_WEIGHT} 的整数，已改回 {weight}：{item['name']}", "warn")
        weight = max(MIN_WEIGHT, min(MAX_WEIGHT, weight))
        item["weight"] = weight
        if var.get() != str(weight):
            var.set(str(weight))

    def _commit_all_weights(self) -> None:
        for key in KEY_ORDER:
            for item in self.items[key]:
                var = self._weight_vars.get(id(item))
                if var is not None:
                    self._commit_weight(item, var)

    def _bump_weight(self, item: dict, delta: int) -> None:
        weight = max(MIN_WEIGHT, min(MAX_WEIGHT, int(item["weight"]) + delta))
        item["weight"] = weight
        var = self._weight_vars.get(id(item))
        if var is not None:
            var.set(str(weight))

    def _remove(self, key: str, item: dict) -> None:
        if item in self.items[key]:
            self.items[key].remove(item)
            self._refresh_key(key)      # 只动这一行的文件列表；分组计数与过滤在 _apply_filter 里更新
            self._apply_filter()
            self.log(f"「{key}」移除 {item['name']}（文件仍留在包目录里，可手动删）")

    def _on_clear_all(self) -> None:
        if not any(self.items.values()):
            return
        if not messagebox.askyesno("清空", "清空界面上已选的全部文件？（不会删除磁盘上的文件）"):
            return
        self._commit_all_weights()
        for key in KEY_ORDER:
            self.items[key].clear()
        if self._import_tempdir is not None:
            self._import_tempdir.cleanup()
            self._import_tempdir = None
        self._refresh_all()
        self._apply_filter()
        self.log("已清空界面上的全部条目（包目录里的文件未删除）。")

    def _on_add(self, key: str) -> None:
        try:
            chosen = filedialog.askopenfilenames(
                title=f"选择要放进「{key} {KEY_LABELS[key]}」的音频文件",
                filetypes=[("音频文件", "*.ogg *.mp3 *.wav *.flac *.m4a *.aac *.opus"), ("所有文件", "*.*")],
            )
        except Exception as e:
            self.log(f"打开文件选择框失败：{e}", "err")
            return
        if not chosen:
            return

        self._commit_all_weights()
        added = 0
        for raw in chosen:
            src = Path(raw)
            if not src.is_file() or src.suffix.lower() not in AUDIO_EXTS:
                self.log(f"添加失败：文件不存在或格式不支持 — {src}", "err")
                continue
            if any(item.get("src") == str(src) for item in self.items[key]):
                self.log(f"「{key}」已添加源文件 {src.name}，跳过重复添加（需要更高概率就调权重）", "warn")
                continue
            _, weight = split_weight_marker(src.stem)
            file_name = preferred_file_name(src.name)
            self.items[key].append(
                {"name": file_name, "weight": weight, "src": str(src), "dir": None})
            self.log(f"「{key}」暂存 {src.name}（预期包名 {file_name}，权重 {weight}；尚未写盘）", "ok")
            added += 1
        if added:
            # 只重画这个键的文件行；分组标题的"已配置 N"与过滤条件由 _apply_filter 更新
            self._refresh_key(key)
            self._apply_filter()
        self.log(f"「{key}」本次暂存 {added} 个源文件；审查后由玩家手动生成。")

    def _preview(self, item: dict) -> None:
        """试听：交给系统默认播放器打开（本程序不内嵌播放器）。

        已构建的包试听包目录中的成品；未构建时试听源文件。转码后的实际效果需在生成后再验。
        """
        candidates = []
        if item.get("dir") and item.get("name"):
            candidates.append(Path(item["dir"]) / item["name"])
        if item.get("src"):
            candidates.append(Path(item["src"]))
        path = next((p for p in candidates if p.is_file()), None)
        if path is None:
            self.log(f"试听失败：找不到文件 {item.get('name')}（已选目录变过？重新添加即可）", "err")
            return
        try:
            open_in_default_player(path)
            self.log(f"已用系统默认播放器打开：{path}")
        except Exception as e:
            self.log(f"试听失败（{path.name}）：{e}", "err")
            self.log(f"  可以手动用播放器打开它：{path}", "warn")

    # ---------------- 构建 ----------------

    def _show_review(self) -> None:
        """只读审查窗口；打开窗口不创建目录、不复制音频、不写 pack.json。"""
        self._commit_all_weights()
        if not self.pack_name.get().strip() or not any(self.items.values()):
            messagebox.showwarning("审查配置", "请先填写显示名，并至少添加一个音频文件。")
            return
        target = self._require_target()
        if target is None:
            return
        # 预先解析占用冲突，让审查窗口显示真正将使用的目录；这里只读，不建目录。
        try:
            pack_id, target = claim_pack_dir(
                self._out_root_path(), self.pack_id.get(), self.pack_name.get().strip(),
                owned=(self._built_dir == target), log=self.log)
        except ValueError as e:
            messagebox.showerror("审查配置", f"目标目录不可用：{e}")
            return
        if pack_id != self.pack_id.get():
            self.pack_id.set(pack_id)
        win = tk.Toplevel(self.root)
        win.title("审查音乐包配置")
        win.geometry(f"{min(900, self.root.winfo_screenwidth() - 100)}x{min(680, self.root.winfo_screenheight() - 140)}")
        win.transient(self.root)
        win.grab_set()
        ttk.Label(win, text="请逐项核对显示名、作者、输出目录、音频键、来源文件和权重；此页不会生成音乐包。",
                  wraplength=780).pack(fill="x", padx=12, pady=(12, 6))
        lines = [f"显示名：{self.pack_name.get().strip()}",
                 f"作者：{self.pack_author.get().strip() or '（未填写）'}",
                 f"说明：{self.pack_desc.get().strip() or '（未填写）'}",
                 f"目标目录：{target}",
                 f"生成动作：{'更新这个包的清单' if (target / MANIFEST_NAME).is_file() else '创建新包'}",
                 "", "音频映射（包内文件名可能因重名避让而追加序号）："]
        missing_sources = []
        for key in KEY_ORDER:
            for item in self.items[key]:
                src = Path(item.get("src") or "")
                available = src.is_file() or (item.get("dir") and
                           (Path(item["dir"]) / item["name"]).is_file())
                status = "可读取" if available else "缺失，必须返回修改"
                if not available:
                    missing_sources.append(str(src))
                lines.append(f"{key} · {KEY_LABELS[key]} | 权重 {item['weight']} | {src} | {status}")
        needs_pyav = (av is None and any(
            Path(item.get("src") or "").suffix.lower() not in COPY_EXTS
            and not (item.get("dir") and (Path(item["dir"]) / item["name"]).is_file())
            for key in KEY_ORDER for item in self.items[key]))
        if missing_sources:
            lines.extend(["", f"⚠ 有 {len(missing_sources)} 个文件缺失：请返回修改后重新审查，生成按钮已禁用。"])
        if needs_pyav:
            lines.extend(["", "⚠ 非 Ogg 文件需要 PyAV 转码：请先安装 PyAV，再重新审查；生成按钮已禁用。"])
        body = ttk.Frame(win)
        body.pack(fill="both", expand=True, padx=12, pady=6)
        scrollbar = ttk.Scrollbar(body, orient="vertical")
        scrollbar.pack(side="right", fill="y")
        review = tk.Text(body, wrap="word", font=self._body_font, yscrollcommand=scrollbar.set)
        review.pack(side="left", fill="both", expand=True)
        scrollbar.configure(command=review.yview)
        review.insert("1.0", "\n".join(lines))
        review.configure(state="disabled")
        bar = ttk.Frame(win)
        bar.pack(fill="x", padx=12, pady=(4, 12))
        ttk.Label(bar, text="由玩家亲自审查并点击生成", foreground="#8a5100").pack(side="left")
        ttk.Button(bar, text="返回修改", command=win.destroy).pack(side="right")
        generate = ttk.Button(bar, text="生成音乐包", command=lambda: self._generate_from_review(win))
        generate.pack(side="right", padx=(0, 10))
        if missing_sources:
            generate.state(["disabled"])
            self.log(f"审查发现 {len(missing_sources)} 个源文件缺失，已禁用生成。", "err")
        elif needs_pyav:
            generate.state(["disabled"])
            self.log("审查发现非 Ogg 源文件，但未安装 PyAV；安装后重开审查窗口。", "err")

    def _generate_from_review(self, review_window: tk.Toplevel) -> None:
        review_window.destroy()
        self._on_build()

    def _ensure_placed(self, writer: PackWriter, item: dict, pack_dir: Path) -> str | None:
        """确保条目对应的文件在包目录里；输出目录变过或文件被删就重新放一份。"""
        target = pack_dir / item["name"]
        if item.get("dir") == str(pack_dir) and target.is_file():
            return item["name"]
        src = item.get("src")
        if src and Path(src).is_file():
            try:
                file_name = writer.place(Path(src))
            except Exception as e:
                self.log(f"  重新放入失败，已跳过：{item['name']} — {e}", "err")
                return None
            self.log(f"  重新放入 {Path(src).name} → {file_name}")
            item["name"], item["dir"] = file_name, str(pack_dir)
            return file_name
        if target.is_file():
            return item["name"]  # 源文件没了，但之前放进目标目录的那份还在
        self.log(f"  缺少文件，已跳过：{item['name']}（源：{src or '未知'}）", "warn")
        return None

    def _on_build(self) -> None:
        self._commit_all_weights()
        name = self.pack_name.get().strip()
        if not name:
            self.log("「显示名」必填：玩家在游戏里就是靠它认这个包的。", "err")
            messagebox.showwarning("构建音乐包", "请先填「显示名」。")
            return
        if not any(self.items.values()):
            self.log("还没有添加任何文件，先「添加文件」再构建。", "err")
            messagebox.showwarning("构建音乐包", "还没有添加任何文件。")
            return

        # 包目录：id 是我们的就一直用它（重新构建写回同一个目录）；万一那个目录里
        # 已经躺着别人的包，就换一个新 id —— 绝不静默顶掉别人的清单。
        out_root = self._out_root_path()
        try:
            validate_pack_id(self.pack_id.get())
            here = out_root / self.pack_id.get()
            pack_id, pack_dir = claim_pack_dir(
                out_root, self.pack_id.get(), name,
                owned=(self._built_dir == here), log=self.log)
        except ValueError as e:
            self.log(f"包 id 不可用：{e}", "err")
            messagebox.showerror("构建音乐包", f"包 id 不可用：{e}")
            return
        if pack_id != self.pack_id.get():
            self.pack_id.set(pack_id)   # 换了 id，界面上的目标目录跟着变

        sounds: dict[str, list[tuple[str, int]]] = {}
        writer = PackWriter(pack_dir, log=self.log)
        try:
            writer.ensure_dir()
        except OSError as e:
            self.log(f"无法创建包目录 {pack_dir}：{e}", "err")
            return

        manifest_path = pack_dir / MANIFEST_NAME
        if manifest_path.is_file():
            self.log(f"这个包以前构建过，将更新它的清单：{manifest_path}")

        for key in KEY_ORDER:
            entries: list[tuple[str, int]] = []
            for item in self.items[key]:
                file_name = self._ensure_placed(writer, item, pack_dir)
                if file_name:
                    entries.append((file_name, int(item["weight"])))
            if entries:
                sounds[key] = entries

        if not sounds:
            self.log("没能把任何文件放进包目录（原因见上面的日志），没有可写的内容。", "err")
            messagebox.showwarning("构建音乐包", "没有可写的内容。")
            return

        try:
            path = writer.write_manifest(
                name=name,
                author=self.pack_author.get().strip(),
                description=self.pack_desc.get().strip(),
                sounds=sounds,
            )
        except OSError as e:
            self.log(f"写入 {MANIFEST_NAME} 失败：{e}", "err")
            messagebox.showerror("构建音乐包", f"写入失败：{e}")
            return
        except Exception as e:
            self.log(f"构建失败：{e}", "err")
            messagebox.showerror("构建音乐包", f"构建失败：{e}")
            return

        # 记下这个目录：以后重新构建它就属于我们自己的包，不再当成"别人的包"
        self._built_dir = pack_dir
        self._refresh_all()
        keys_used = len(sounds)
        entries = sum(len(v) for v in sounds.values())
        referenced = {file_name for per_key in sounds.values() for file_name, _ in per_key}
        leftovers = [p.name for p in pack_dir.glob("*.ogg") if p.name not in referenced]
        self.log(f"音乐包已生成：{manifest_path}（{keys_used} 个键、{entries} 个文件）", "ok")
        self.log(f"  游戏里显示为「{name}」；包 id {pack_id} 只是目录名，不用记。")
        if leftovers:
            self.log(f"  包目录里还有 {len(leftovers)} 个未被清单引用的 .ogg（上次构建的残留，可手动删）", "warn")
        messagebox.showinfo("构建音乐包",
                            f"已生成：\n{path}\n\n游戏里的「配置 → 音乐包」按显示名「{name}」找它。")

    # ---------------- 使用说明 ----------------

    def _show_help(self) -> None:
        """弹出使用说明（置顶、居中）：这个程序在做什么、包 id 与显示名、审查步骤、生成后怎么用。

        启动时自动弹一次（见 `_open_help_on_start`），之后随时可以用右上角的按钮再打开。
        """
        win = tk.Toplevel(self.root)
        win.title("使用说明 —— Crafty Cards 音乐包制作程序")
        win.geometry(f"860x{min(640, self.root.winfo_screenheight() - 160)}")
        win.transient(self.root)            # 跟着主窗口走，不单独占任务栏
        win.attributes("-topmost", True)    # 置顶：弹出来就看得见，不会被主窗口压在下面

        # 打包顺序有讲究：底部按钮条先占住自己的位置，再放滚动条，最后正文吃满剩下的空间
        bottom = ttk.Frame(win, padding=(12, 8))
        bottom.pack(side="bottom", fill="x")
        ttk.Button(bottom, text="知道了", command=win.destroy).pack(side="right")

        scroll = ttk.Scrollbar(win, orient="vertical")
        scroll.pack(side="right", fill="y")
        # 正文用界面字体（不是默认的新宋体），spacing3 让中文行间透一口气
        text = tk.Text(win, wrap="word", padx=12, pady=10, yscrollcommand=scroll.set,
                       font=self._body_font, spacing3=2)
        text.pack(side="left", fill="both", expand=True)
        scroll.configure(command=text.yview)
        for tag, opts in (
            ("h", {"font": self._body_bold}),       # 只有小节标题是粗体，正文保持正常字重
            ("body", {}),
            ("code", {"foreground": "#1a4f8a"}),    # 命令/路径
            ("dim", {"foreground": "#888888"}),     # 附带说明（指向文档）
        ):
            text.tag_configure(tag, **opts)

        # 一节 = 标题（粗体一行）+ 正文；空行由下面的循环插入，正文里不再手写空行
        sections: list[tuple[str, str]] = [
            ("h", "这个程序做什么"),
            ("body", """把你自己准备的音频整理成一个「音乐包」。游戏里的音效与背景音乐全部来自音乐包
——模组本身不含任何音频；要改包的内容只能用这个程序重新做（游戏内只读取与选用），
所以不会出现「游戏把包改坏」的情况。
下面按「怎么用 → 细节参考 → 进游戏之后 → 出问题怎么办」排列；关掉后随时可以点右上角的
「使用说明」再看。"""),

            ("h", "四步操作"),
            ("body", """① 新建包时填「包信息」；已有音乐包可点「导入包目录…」或「导入 ZIP…」，
   导入后会生成新 id，原包不变。三包合集 ZIP 请先解压，再逐个导入包目录。
   「显示名」必填——游戏里就按它认这个包；作者、说明随意填。
② 给「音频键」添加文件：想替换哪个声音就点那一行的「添加文件」（可一次选多个，不是 .ogg 的
   会在生成时自动转码），再给每个文件设「权重」；点「试听」可以用系统默认播放器先听一遍。
③ 点「审查配置…」，逐项核对包信息、输出目录、音频键、源文件和权重。
④ 玩家亲自在审查窗口点「生成音乐包」，此时才复制/转码音频并写出 pack.json；然后进游戏选用。
键很多时，用列表上方的「只看已配置的键」和搜索框定位；每行右侧的状态列显示该键配了几个文件。
要做第二个包就点「新建包」；同一个包想改内容就别点它——直接改完重新审查并生成。"""),

            ("h", "「音频键」是什么"),
            ("body", """一个音频键 = 游戏里一个会被触发的声音，共 61 个：
   · 音效（7）：发牌、叫分、出牌（通用）、炸弹、过牌、结算胜负 —— 各响一次
   · 背景音乐（6）：按牌局阶段循环播放
   · 牌型语音（48）：出牌时按牌型念出来（单张 A、对子 K、顺子…）
没添加文件的键不会有声音：音效退回原版音效、BGM 不播（不会拿原版音乐凑数）、牌型语音退回
「出牌（通用）」。
音效都由牌局状态的变化驱动、在你本机播放，所以别人叫分、出牌、过牌时你也听得到，音量各人各调。
「结算」有两组键、游戏里都会播：win / lose 是结算那一刻的一次性音效，bgm_win / bgm_lose 是
结算阶段循环的背景音乐——BGM 那组的中文名带「BGM：」前缀就是这个缘故，不是重复的键。"""),

            ("h", "「权重」是什么"),
            ("body", """权重 = 同一个键下多个文件被挑中的相对概率。比如「过牌」加了三条、权重分别写
3 / 1 / 1，长期听下来大约按 3:1:1 出现——想让声音不重复，就给同一个键多加几个文件。
数字框可以直接输入（1~99），也可以用 - / + 微调。0 是游戏内的概念（= 不播这一条），包里必须 ≥1。"""),

            ("h", "音频文件的要求"),
            ("body", """· 必须是 Ogg Vorbis 才能在游戏里播出——.mp3 / .wav / .flac 等本程序会自动转码。
· 包内文件名会自动清洗成合法名字（只保留英文、数字、. _ -），并且绝不覆盖同名文件：
  内容相同就复用，内容不同就加 _2、_3。
· 「试听」用你系统的默认播放器打开文件（本程序不内嵌播放器）：音质、音量、输出设备都跟你
  平时听的一致，反复对比也方便。"""),

            ("h", "包 id、显示名、作者各是什么"),
            ("body", """「显示名」和「作者」是给玩家看的：游戏里的音乐包列表显示这两项，你按显示名认自己的包。
「包 id」是包目录的名字（config/crafty_cards/soundpacks/<包id>/），由程序自动生成 8 位随机
字符——你不能填，也不需要记。
为什么不让填：一个目录里只放得下一份清单，两个包共用 id（比如都取名 my_pack）时，后构建的
会把前一个顶掉——那个包在游戏里就没了声音，还很难查。随机 id 让两个包撞不到一起，于是一台
机器上可以并排放好几个包随时切换。想删掉一个包，按 id 删掉那个目录即可。"""),

            ("h", "做好之后怎么用"),
            ("body", f"""包目录默认是 {DEFAULT_OUT}/<包id>/，构建完成的日志里会写出完整路径。然后在游戏里：
""" + GAME_STEPS),

            ("h", "游戏里还能调什么"),
            ("body", """游戏侧只在「音乐包」一页里留了三个旋钮：
""" + GAME_PARAMS + """
音频内容（哪个键用哪些文件、各占多大权重）**全部由包决定**——游戏内不再有"逐键指派文件 /
逐条调权重 / 试听"这些操作，所以包里的权重就是最终权重。想少播某条就把它的权重写小，
想不播就从清单里去掉；某个键在包里没有条目，就等于这个键没有声音，落到下一档。"""),

            ("h", "听不到声音？先查这三处"),
            ("body", """① 有没有点「保存并应用」：换包、改权重、加文件之后都要在音频页点一次；游戏内的「试听」
   也要求先保存。
② BGM 的声道音量：BGM 默认走原版「玩家」滑块，它被调成 0 就没有 BGM。要换声道请改
   config/crafty_cards/sounds.json 的 bgmCategory（players / music / ambient / master），重启生效。
③ 这个键在包里到底有没有条目：游戏内不再显示逐键的文件列表，直接看 pack.json（或本程序的
   键列表）——没有条目的键本来就没有声音，音效退回原版。模组不含音频，所以一个包都没选时，
   听到的就是原版音效。"""),

            ("h", "其它"),
            ("body", """· 重新构建同一个包不会堆出重复文件（同名同内容复用），也不会动到别的包。
· 「清空已选文件」只清界面上的选择，不删磁盘上的文件；「新建包」会连包信息一起清掉并换新 id。
· 脚本化（无界面，与界面同一套逻辑）："""),
            ("code", "python tools/soundpack_maker.py --cli <源目录> [包id] [--name 显示名] [--out 输出根目录]"),
            ("body", """  包 id 一般不用写（会自动生成）；只有脚本要往固定目录里重写时才显式传，这时那个目录里是
  别人的包会直接报错而不是覆盖它。省了 --name 就用源目录名当显示名。
  文件名就是键名，所以源目录可以直接按名字摆好：pass_1.mp3 → 过牌、dan1.wav → 单张 A、
  bgm_playing.wav → 对局 BGM；权重也能写进文件名：pass_2_w3.mp3 = 权重 3。"""),

            ("dim", "更细的格式说明（pack.json 字段、游戏侧的优先级与生成机制）见仓库里的 docs/音乐包格式.md"),
        ]
        first = True
        for tag, content in sections:
            if not first:       # 小节之间空一行（正文里不再手写空行）
                text.insert("end", "\n")
            first = False
            text.insert("end", content + "\n", tag)
        text.configure(state="disabled")

        # 位置按主窗口算，不写死坐标：弹在主窗口上偏三分之一处（读者视线自然落点）
        win.update_idletasks()
        x = self.root.winfo_rootx() + (self.root.winfo_width() - win.winfo_width()) // 2
        y = self.root.winfo_rooty() + (self.root.winfo_height() - win.winfo_height()) // 3
        win.geometry(f"+{max(0, x)}+{max(0, y)}")
        win.bind("<Escape>", lambda _event: win.destroy())
        win.lift()
        win.focus_force()    # 启动时自动弹的那一次要真的浮到最前面，而不是只闪一下

    def _open_help_on_start(self) -> None:
        """启动时自动弹一次使用说明（置顶）。

        失败也**不能拦住程序启动**——说明打不开是小事，程序起不来是大事。
        """
        try:
            self._show_help()
        except Exception as e:  # 没有窗口管理器之类
            self.log(f"打开使用说明失败（不影响继续使用）：{e}", "warn")

    def run(self) -> None:
        self.root.mainloop()


def gui_main() -> int:
    """打开图形界面。tkinter 不可用（无显示环境/未装）时给一条可执行的建议。"""
    if tk is None:
        print("没有找到 tkinter，无法打开图形界面。", file=sys.stderr)
        print("可以改用命令行模式：python tools/soundpack_maker.py --cli <源目录> [--name 显示名]",
              file=sys.stderr)
        print(f"（导入 tkinter 的报错：{TK_IMPORT_ERROR}）", file=sys.stderr)
        return 1
    try:
        app = SoundPackMakerApp()
    except Exception as e:  # 无显示环境会在这里抛 TclError，别让用户只看到堆栈
        print(f"图形界面启动失败：{e}", file=sys.stderr)
        print("（没有图形环境时用命令行模式：--cli <源目录> [--name 显示名]）", file=sys.stderr)
        return 1
    app.run()
    return 0


# --------------------------------------------------------------------------- #
# 入口
# --------------------------------------------------------------------------- #


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="soundpack_maker.py",
        description="制作 Crafty Cards 音乐包：图形界面（默认）或无界面命令行（--cli）。",
        epilog=(
            "音频键：音效 deal bid play bomb pass win lose；"
            "背景音乐 bgm_waiting bgm_playing bgm_clutch bgm_rocket bgm_win bgm_lose；"
            "牌型语音 dan1..dan15 dui1..dui13 tuple1..tuple13 sandaiyi shunzi liandui feiji "
            "sidaier sidailiangdui wangzha。"
            "更多说明见文件开头的文档字符串。"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--cli", action="store_true",
                        help="命令行模式（不开界面）：需要一个源目录")
    parser.add_argument("--dry-run", action="store_true",
                        help="配合 --cli 预览识别结果、权重和跳过文件；不创建目录或写入文件")
    parser.add_argument("source", nargs="?", help="源目录：存放音频文件的目录（--cli 用，会递归扫描）")
    parser.add_argument("pack_id", nargs="?",
                        help="包 id（--cli 用，一般不用填）：默认随机生成 8 位十六进制，"
                             "只有脚本要往固定目录里重写时才需要显式指定")
    parser.add_argument("--name", default=None,
                        help="显示名：玩家在游戏里靠它认包（--cli 省略时用源目录名）")
    parser.add_argument("--author", default="", help="作者（会显示在游戏里的包列表里）")
    parser.add_argument("--desc", default="", help="说明")
    parser.add_argument("--out", default=DEFAULT_OUT,
                        help=f"输出根目录，默认 {DEFAULT_OUT}（包目录建在它下面）")
    return parser


def main(argv: list[str] | None = None) -> int:
    # PowerShell reads redirected native output as UTF-8; Python may otherwise
    # choose the system GBK code page and turn Chinese preview text into mojibake.
    if sys.platform.startswith("win") and not sys.stdout.isatty() \
            and hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = build_parser()
    args = parser.parse_args(list(sys.argv[1:] if argv is None else argv))
    if args.cli:
        if not args.source:
            parser.error("--cli 模式需要 <源目录> 参数")
        return run_cli(args)
    if args.dry_run:
        parser.error("--dry-run 只能与 --cli 一起使用")
    if args.source or args.pack_id:
        print("位置参数只在 --cli 模式下使用。", file=sys.stderr)
        print("命令行：python tools/soundpack_maker.py --cli <源目录> [包id] [--name … --out …]",
              file=sys.stderr)
        return 2
    return gui_main()


if __name__ == "__main__":
    sys.exit(main())
