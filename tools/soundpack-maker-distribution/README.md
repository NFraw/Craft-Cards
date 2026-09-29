# Crafty Cards 音乐包制作工具 1.0.1

下载 Release 中的 `crafty-cards-soundpack-maker-1.0.1.zip`，解压后在 Windows 双击 `启动音乐包制作工具.bat`。此工具与模组安装包放在同一 GitHub Release，无须寻找第二个仓库。1.0.1 增加了导入现有音乐包目录或单包 ZIP 的功能。

需要 Python 3.10 或更新版本，且安装时勾选 tkinter（Windows 官方安装器通常包含）。已有 `.ogg` 音频无需额外依赖；导入 MP3/WAV 等并转码时，在命令行运行 `python -m pip install av`。工具不会随包附带 Python、PyAV 或音频素材。

在窗口中填写显示名、作者和输出目录，按音频键添加源文件并调权重。添加阶段只登记映射。点“审查配置…”核对清单，再由你亲自点击“生成音乐包”；只有最后一步才写 `pack.json` 并复制或转码音频。生成后把整个包目录放到 Minecraft 实例的 `config/crafty_cards/soundpacks/`，在游戏中扫描、选用并保存。

已有音乐包可点击“导入包目录…”或“导入 ZIP…”，读取现有的 `pack.json`、音频映射和权重后继续编辑。导入本身不会写入输出目录；审查后点击“生成音乐包”会生成一份新包，原包保持不变。三包合集 ZIP 含多个包，请先解压并逐个选择包目录。

也可运行 `python soundpack_maker.py --help` 使用命令行；`--dry-run` 只预览。批量制作可参考 `Harness音乐包制作提示词.md`。音频键与格式见 `音乐包格式.md`。只分享你有权再分发的音频。

本工具 zip 不含音频；1.21.1 的三个历史音乐包另有[音频权利说明](../../release-materials/AUDIO-RIGHTS.md)，其来源尚待核实，不能因工具可制作音乐包就推定那些录音可公开分发。
