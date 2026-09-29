# 素材与许可核对

仓库根目录的 `LICENSE` 是 GPL-3.0-only；构建元数据也使用该标识。但本地参考包 `PlayingCards-master` 来自 OmbreMoon 的 PlayingCards，且其上游为 Calemi 的 Playing Cards。逐字节对比发现当前资源中有 69 张 PNG 与参考包完全相同，包括卡牌、筹码和方块纹理。此前“全部贴图原创”的表述已更正；完整来源链见 [CREDITS.md](../../CREDITS.md)。

| 类别 | 仓库位置 | 当前处理 | 发布前核对 |
| --- | --- | --- | --- |
| Java/Groovy/脚本源码 | `common/`、`src/`、`fabric/`、`forge/`、`versions/`、`tools/` | 拟按根目录许可公开 | 来源、继承项目许可、贡献者权利 |
| 继承及改造贴图、模型、语言资源 | `common/src/main/resources/` | 部分源于 PlayingCards；与 mod jar 一同分发 | 逐文件来源、Calemi 与 OmbreMoon 的授权、改造部分的许可标示 |
| 模组图标 | `common/src/main/resources/logo.png`、`release-materials/logo.png` | 由项目脚本组合牌桌和卡牌纹理 | 因包含继承牌面图案，授权核对不能只看新图标文件 |
| 1.21.1 三个历史音乐包 | `learning-soundpacks/` | 仓库与 Release 提供带音频的学习样本；mod jar 不含音频 | 旧导入记录指向他人作品；逐包风险见[音频权利说明](../../release-materials/AUDIO-RIGHTS.md) |
| 截图与日志 | `docs/evidence/`、`build/` | 前者可能公开，后者不由 Git 跟踪 | 玩家名、世界路径、服务器地址、私密信息 |
| 历史设计稿 | `docs/ReferenFiles/`、`docs/superpowers/` | 与当前实现分开标明 | 第三方引用、过期承诺、是否要纳入公开仓库 |

不能因为模组 jar 不含音频，就推断公开 Git 仓库里的音乐包已有再分发授权。三个学习样本的风险已在 [音频权利说明](../../release-materials/AUDIO-RIGHTS.md)披露；如有侵权，请联系下架。对继承的牌面和图标同样如此：本地参考包标 `GNU-LGPLv3`，OmbreMoon 发布页标 GPLv3，Calemi 发布页标 All Rights Reserved，其 GitHub 仓库又显示 GPL-3.0；应核对具体版本与文件授权，不能仅凭本仓库 `LICENSE` 作结论。不要把 Minecraft、加载器、Fabric API 的名称或图标描述成项目自有商标。
