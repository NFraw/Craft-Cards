# 来源、改造与致谢

Crafty Cards 的早期卡牌/筹码模组基础取自 [OmbreMoon 的 PlayingCards 项目](https://github.com/OmbreMoon/PlayingCards)（发布名称 [Playing Cards & Chips](https://www.curseforge.com/minecraft/mc-mods/playing-cards-chips)）。开发时使用的本地参考包是 `PlayingCards-master`，其 `gradle.properties` 标明 `Playing Cards`、作者 `OmbreMoon`、版本 `2.0.1`、Minecraft `1.21.1`。本项目不是该项目的官方续作，也没有得到其作者的背书。

OmbreMoon 在发布页说明，Playing Cards & Chips 又移植自 [Calemi 的 Playing Cards](https://www.curseforge.com/minecraft/mc-mods/playing-cards)，原始代码和美术由 Calemi 创作。这里同时感谢 Calemi 与 OmbreMoon，并保留这条来源链。Crafty Cards 在此基础上加入三人联机斗地主规则、服务端牌局/快照、HUD 与世界内持牌、按桌筹码设置、音乐包接口、多加载器和多版本适配；具体文件不能仅凭这段功能概述判定为全新或完全继承。

## 本地核对结果

- 对 `PlayingCards-master` 与当前 `src/main` 相关 Java、PNG、JSON、NBT 文件做 SHA-256 对比：参考包 242 个相关文件中，**69 个 PNG 与当前资源逐字节一致**。其中包括牌面、牌背、牌堆、筹码和部分方块纹理。未发现逐字节相同的 Java 文件；包名/接口经过移植，**这不等于代码没有继承关系**。
- 当前 `logo.png` 由 `tools/make_icon.py` 组合本仓库牌桌纹理、红色牌背与小王图案。它并非完全独立于上游美术的原创图标；发布图标应与上游素材一起核对授权。
- 本地参考包的 `gradle.properties` 写 `GNU-LGPLv3`；其 `TEMPLATE_LICENSE.txt` 是 NeoForged MDK 模板的 MIT 许可声明，**不能作为上游模组代码或美术的许可证明**。OmbreMoon 的 CurseForge 页面显示 GPLv3；Calemi 的 CurseForge 页面显示 All Rights Reserved，而其 GitHub 仓库显示 GPL-3.0。各页面/文件的许可标示存在差异，尚未逐文件确认美术的再分发授权。

本仓库根目录 `LICENSE` 表达 Crafty Cards 拟采用的 GPL-3.0-only；它不能替代上游权利人的许可，也不能把继承素材自动变成本项目独有。公开源码、发布 jar、上传图标之前应核对相关版本、来源与授权范围；如无法确认，先取得授权或替换相关素材，并相应更新本说明。
