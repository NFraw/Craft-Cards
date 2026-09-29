# Crafty Cards 1.0.0 — 三人联机斗地主

在 Minecraft 世界中放置牌桌，三名玩家加入后叫地主、选牌、出牌并由服务端结算。支持 HUD 手牌与倒计时、玩家身前的牌、主动观战和可选物品筹码。

## 下载与安装

从本 Release 下载**与你的 Minecraft 版本和加载器完全相同**的 jar，客户端和服务器都放入 `mods`。支持 Minecraft 1.21–1.21.11 的 NeoForge、Fabric、Forge，唯独 Forge 1.21.2 没有目标包。需要 Java 21；Fabric 另需对应版本的 Fabric API。`crafty-cards-1.0.0-all-loaders.zip` 集中收录 35 个 jar、版本依赖表、清单及 SHA-256；也可以直接下载单个 jar。

要制作或修改音乐包，下载同一 Release 的 `crafty-cards-soundpack-maker-1.0.1.zip`（1.0.0 未提供导入已有包功能），解压后阅读 README，在 Windows 双击启动器（本机需 Python 3.10+）。1.0.1 可导入包目录或单包 ZIP，读取映射和权重；“添加文件”也只暂存映射。在审查窗口核对后亲自点击“生成音乐包”才写入新包，原包保持不变。模组 jar 不含音频。三个 1.21.1 的带音频学习样本可从仓库 `learning-soundpacks/` 查看，Release 另有单包与合集压缩包。它们的录音可能涉及第三方权利，授权尚未逐项确认；详见[音频权利说明](AUDIO-RIGHTS.md)。如有侵权，请联系下架。

## 验证范围与限制

35 个组合均重新构建并通过各自的 123 项公共单测与安装包资源检查。NeoForge 1.21.1 是先前上机验收的视觉基准；该目标另有 35 项 GameTest 回归记录。其他目标的图像、全屏 HUD、底牌位置、配置文字、实际听音及干净环境中的三人联机仍需逐项人工核对。构建和单测通过不等于所有版本的游玩体验已获实机确认。详情见仓库的验证文档和验收证据。

本项目改造自 OmbreMoon 的 PlayingCards（其上游为 Calemi 的 Playing Cards）；来源与许可说明见仓库 `CREDITS.md`。请在发布前完成继承素材及音乐包的权利核对。
