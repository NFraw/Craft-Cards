# Crafty Cards 发布素材包（本地待审）

这个文件夹集中存放模组发布页需要的标识图片、名称与描述。尚未上传或发布；GitHub 仓库地址确定后再填写实际链接。

| 文件 | 用途 |
| --- | --- |
| `logo.png` | 正方形模组标识，512 × 512 PNG；直接复制自 `common/src/main/resources/logo.png` |
| `listing.zh-CN.md` | 中文发布页：名称、一句话简介、完整描述、依赖和限制 |
| `listing.en-US.md` | 英文发布页：与中文一致的发布文案 |
| `metadata.json` | 可供自动填写或核对的结构化字段；不是上传平台专用格式 |

`logo.png` 为 512 × 512 PNG，SHA-256：`3D086DC1783E483B14B638BC236F768BF58E1F5DDA33E6C6CF19B5C8171B0938`；与模组资源目录的图标逐字节相同。

建议项目品牌统一为 **Crafty Cards**，模组 ID 为 `crafty_cards`，作者显示名为 **JokerNan**；1.0.0 的副标题可写“斗地主”。每个加载器与 Minecraft 版本应上传各自的 jar，不要用一份“通用 jar”覆盖整个矩阵。发布时为对应安装包单独写明 Minecraft 版本、加载器、依赖与 SHA-256。

图标由仓库脚本 `tools/make_icon.py` 合成：牌桌绿色呢面背景、红色牌背和小王牌面。牌背和牌面纹理继承自 PlayingCards，不能称为完全原创图标。公开前需按 [来源与致谢](../CREDITS.md) 和 [素材许可清单](../docs/open-source/ASSETS.md) 核对美术及音频的分发权；本文件夹目前仅供审阅。

**1.0.0 提供三人联机斗地主**，含 HUD 手牌、世界内牌面、旁观与可选筹码。当前没有 AI 单人模式，不承诺跨加载器混服；音乐需要客户端另装音乐包。自动验收与人工视觉/听音的界限见 [验证文档](../docs/open-source/TESTING.md)。

音乐包制作工具以 `crafty-cards-soundpack-maker-1.0.0.zip` 附在同一个 GitHub Release，下载后解压并双击 Windows 启动器即可打开（需本机 Python）。模组 jar 不包含音频；源码仓库的 `learning-soundpacks/` 收录 1.21.1 的三个带音频学习样本，Release 另备可直接下载的压缩包。[音频权利说明](AUDIO-RIGHTS.md)记录第三方来源与授权风险。如有侵权，请联系下架。
