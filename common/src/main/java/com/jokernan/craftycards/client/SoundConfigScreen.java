package com.jokernan.craftycards.client;
import com.jokernan.craftycards.platform.SoundPackInjection;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频页（音乐包）——**音频的唯一入口**。
 *
 * <p>音频的来源只有一个：{@link SoundPack 音乐包}
 * （{@code config/crafty_cards/soundpacks/<包id>/}，由 {@code tools/soundpack_maker.py} 生成）。
 * 游戏侧只做三件事：<b>选包、扫描、看这个包覆盖了哪些键</b>，外加总开关与音量这两个播放开关。</p>
 *
 * <p>曾经的「音效与语音 / 牌型语音 / 背景音乐」三页与逐键详情页（在游戏内把某个 .ogg 指派给某个
 * 音频键、逐条调权重、试听、增删自备文件）已全部去除：那是一条绕过音乐包的"在游戏里直接改音频"
 * 的路，与"音乐包是唯一接入接口"相冲突——两处都能改音频，排查问题时先要问清"你到底改的哪边"。
 * 现在要改音频就去改包（制作程序），游戏只读包、不改包。</p>
 */
public class SoundConfigScreen extends ConfigScreenBase {
    public SoundConfigScreen(Screen parent) {
        super(parent, "音乐包", "音频 › 音乐包");
    }

    @Override
    protected void buildRows() {
        SoundEditor.begin();

        list.header("播放");
        list.row(toggleRow("启用模组音频", "关掉后音效退回原版音效、BGM 不播（音乐包也不加载）",
            SoundEditor::enabled, SoundEditor::enabled));
        list.row(percentRow("音量", "音效与 BGM 共用这一个音量",
            SoundEditor::volume, SoundEditor::volume));
        list.note("BGM 用哪个原版音量滑块、是否压低原版音乐，在 sounds.json 里（不再由界面调整）");

        list.header("当前音乐包");
        list.row(customRow("选用音乐包", "包里的文件按 pack.json 声明的音频键与权重播放",
            packButton()));
        list.row(customRow("重新扫描", "把音乐包放进目录后点这里，不用重开界面",
            Button.builder(Component.literal("扫描"), b -> {
                CustomSoundConfig.reloadPacks();
                status = "已扫描：" + CustomSoundConfig.packs().size() + " 个音乐包";
                rebuild();
            }).size(56, 20).build()));

        list.header("已发现的音乐包");
        List<SoundPack> packs = CustomSoundConfig.packs();
        if (packs.isEmpty()) {
            list.note("还没有音乐包。用制作程序（tools/soundpack_maker.py）做一个，");
            list.note("放进下面这个目录，再点上面的「扫描」。");
        } else {
            for (SoundPack pack : packs) {
                boolean active = pack.id().equals(SoundEditor.pack());
                list.row(customRow(pack.name(), packHint(pack),
                    Button.builder(Component.literal(active ? "使用中" : "选用"), b -> {
                        SoundEditor.pack(pack.id());
                        status = "已选音乐包：" + pack.name() + "（保存后生效）";
                        rebuild();
                    }).size(56, 20).build()));
            }
        }
        list.note("音乐包只能由制作程序修改（游戏内只读取与选用），因此不会出现「游戏把包改坏」");

        list.header("目录");
        list.note("音乐包：" + CustomSoundConfig.soundPackDir());
    }

    /** 音乐包一行的说明：作者 / 覆盖键数 / 候选条目数。 */
    private String packHint(SoundPack pack) {
        StringBuilder sb = new StringBuilder();
        if (!pack.author().isEmpty()) sb.append("作者 ").append(pack.author()).append(" · ");
        sb.append("覆盖 ").append(pack.coveredKeys().size()).append(" 个键");
        int entries = 0;
        for (String key : pack.coveredKeys()) entries += pack.entriesFor(key).size();
        sb.append(" · ").append(entries).append(" 个文件");
        if (!pack.description().isEmpty()) sb.append(" · ").append(pack.description());
        return sb.toString();
    }

    /** 音乐包选择按钮（在"不使用 + 各音乐包"之间循环）。 */
    private Button packButton() {
        List<String> options = new ArrayList<>();
        options.add("");
        for (SoundPack pack : CustomSoundConfig.packs()) options.add(pack.id());
        return Button.builder(packLabel(), b -> {
            String current = SoundEditor.pack();
            int idx = options.indexOf(current);
            String next = options.get((idx + 1) % options.size());
            SoundEditor.pack(next);
            b.setMessage(packLabel());
            status = "";
            rebuild();
        }).size(CONTROL_WIDTH + 60, 20).build();
    }

    private Component packLabel() {
        String id = SoundEditor.pack();
        if (id.isEmpty()) return Component.literal("不使用音乐包");
        for (SoundPack pack : CustomSoundConfig.packs()) {
            if (pack.id().equals(id)) return Component.literal(pack.name());
        }
        return Component.literal(id + "(缺失)");
    }

    @Override
    protected void buildFooter() {
        addFooterButton("保存并应用", () -> {
            boolean had = SoundEditor.dirty();
            SoundEditor.commit();
            status = had
                ? "已保存并重载：" + CustomSoundConfig.resolvedTable().size() + " 个键、"
                    + SoundPackInjection.lastCopiedCount() + " 个文件"
                : "没有改动需要保存";
            rebuild();
        });
    }

    @Override
    protected String hint() {
        if (SoundEditor.dirty()) return "有未保存的改动（改的是选包/音量/总开关）";
        if (!SoundEditor.enabled()) return "模组音频已关闭：音效走原版，BGM 不播";
        if (CustomSoundConfig.bgmCategoryMuted()) {
            return "注意：原版「" + SoundEditor.bgmLabel() + "」音量为 0，BGM 听不到"
                + "——调高该音量滑块，或改 sounds.json 的 bgmCategory";
        }
        if (CustomSoundConfig.resolvedCandidateTotal() == 0) {
            return "当前没有音频：在下面选一个音乐包（没有包就用制作程序做一个）";
        }
        return "音频改动需经资源重载才生效，故只在这里点「保存并应用」才落地";
    }

    @Override
    protected int hintColor() {
        return SoundEditor.dirty() || !SoundEditor.enabled()
            || CustomSoundConfig.bgmCategoryMuted()
            || CustomSoundConfig.resolvedCandidateTotal() == 0 ? 0xFFFFAA00 : 0xFF808080;
    }
}
