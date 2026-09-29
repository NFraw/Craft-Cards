package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * 设置主页：按分类进入各配置页。
 *
 * <p>多层级结构的第一层。此前是三块配置挤在一层（音频界面底部一行 7 个控件、
 * 玩法入口还挂在音频界面里），这里改成：<b>主页只回答"有哪些配置、现在各是什么状态"</b>，
 * 具体设置进各自的页面。每一类都带一句当前状态摘要，不用点进去就知道现在是什么配置。</p>
 *
 * <p>层级：</p>
 * <pre>
 * 设置主页（本页）
 *  ├─ 音乐包          → SoundConfigScreen      （音频的唯一入口）
 *  ├─ 玩法设置（房主） → ServerPlayConfigScreen
 *  └─ 渲染参数        → RenderConfigScreen
 * </pre>
 *
 * <p>音频只剩一项入口：音频的来源只有音乐包，游戏内不再提供"逐键指派文件 / 逐条调权重 /
 * 试听"这类直接改音频的操作（那会与"音乐包是唯一接入接口"冲突）。</p>
 */
public class ConfigHomeScreen extends ConfigScreenBase {
    public ConfigHomeScreen(Screen parent) {
        super(parent, "Crafty Cards 设置", "选一个分类进入（每项后面是当前状态）");
    }

    @Override
    protected void buildRows() {
        SoundEditor.begin();

        list.header("音频");
        list.row(customRow("音乐包", packSummary(), enter(new SoundConfigScreen(this))));
        list.note("音频只有一个来源：音乐包（用 tools/soundpack_maker.py 制作）");

        list.header("玩法（房主设置）");
        list.row(customRow("玩法设置", playSummary(), enter(new ServerPlayConfigScreen(this))));
        list.note("是否需要筹码、入局距离、观战可见性等；专用服务器请在服务端改");
        list.note("每张桌子还有自己的筹码/底注/门槛：站到桌前 Shift+右键（房主）即可改");

        list.header("渲染");
        list.row(customRow("渲染参数", renderSummary(), enter(new RenderConfigScreen(this))));
        list.note("完整参数在 config/crafty_cards/visual.json，改文件后用 /craftycards reload 生效");

        list.header("配置文件位置");
        list.note(dir());
    }

    /** 有没保存的音频改动时在底部提醒一句：选包/音量在音乐包页改，主页上要看得见。 */
    @Override
    protected String hint() {
        return SoundEditor.dirty() ? "音频有未保存的改动，关闭设置界面会先问你一句" : "";
    }

    /** 主页的按钮关掉的是整个设置界面，叫「完成」比「返回」准确。 */
    @Override
    protected String returnLabel() {
        return "完成";
    }

    /** 提醒类提示（未保存）用橙色，否则玩家容易当成装饰文字略过。 */
    @Override
    protected int hintColor() {
        return 0xFFFFAA00;
    }

    @Override
    public void onClose() {
        // 关闭整个设置界面：有没保存的音频改动就先问一句，别静默丢掉
        if (SoundEditor.dirty()) {
            if (minecraft != null) minecraft.setScreen(new UnsavedAudioScreen(parent, this));
            return;
        }
        SoundEditor.discard();
        super.onClose();
    }

    // === 摘要文字 ===

    /** 一个「进入」按钮（行右侧）。 */
    private AbstractWidget enter(Screen target) {
        return Button.builder(Component.literal("进入"), b -> {
            if (minecraft != null) minecraft.setScreen(target);
        }).size(56, 20).build();
    }

    private String packSummary() {
        if (!SoundEditor.enabled()) return "已关闭模组音频（音效走原版）";
        if (SoundEditor.pack().isEmpty()) {
            return "未选用（已发现 " + CustomSoundConfig.packs().size() + " 个音乐包）";
        }
        SoundPack pack = null;
        for (SoundPack p : CustomSoundConfig.packs()) {
            if (p.id().equals(SoundEditor.pack())) pack = p;
        }
        if (pack == null) return SoundEditor.pack() + "（找不到，请重新选）";
        return pack.name() + " · 覆盖 " + pack.coveredKeys().size() + " 个键 · 音量 "
            + pct(SoundEditor.volume());
    }

    private String playSummary() {
        String view = ServerGameConfig.spectatorsSeeCards ? "观战可看牌" : "观战只看牌背";
        String stake = ServerGameConfig.chipsRequired
            ? "底注 " + ServerGameConfig.chipStake + " · 门槛 " + ServerGameConfig.requiredChips() + " 个"
            : "不需要筹码";
        return stake + " · 入局 " + (int) ServerGameConfig.joinRadius + " 格"
            + " · " + view;
    }

    private String renderSummary() {
        return "手牌 " + RenderConfig.hudCardW + "px"
            + " · 立牌距离 " + RenderConfig.handForwardDist
            + " · 出牌缩放 " + RenderConfig.playedCardScale;
    }

    private static String pct(float v) {
        return Math.round(v * 100) + "%";
    }

    /** 显示配置文件目录（音乐包目录的上一级 = config/crafty_cards）。 */
    private static String dir() {
        Path dir = CustomSoundConfig.soundPackDir().getParent();
        return dir == null ? "config/crafty_cards" : dir.toString();
    }
}
