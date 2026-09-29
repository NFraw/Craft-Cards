package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.init.InitSounds;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 背景音乐播放器：按牌局阶段循环播放音乐包里的 BGM。
 *
 * <p>槽位与阶段的对应见 {@link CustomAudio#bgmFor}（等待 / 进行中 / <b>残局</b> / <b>王炸之后</b> /
 * 结算胜负）。播放什么由**音乐包配了哪些键**决定：主槽位没配就按 {@link CustomAudio#bgmChain}
 * 往下回退（王炸 → 残局 → 进行中），一个都没配才不播——这样给老音乐包加新键不会让背景音乐消失。
 * 同一键配了多首时由原版按权重随机挑。</p>
 *
 * <p>状态切换由客户端 tick 驱动，按"最终解析到的槽位是否变化"决定换曲，因此不会因为快照重复下发
 * （旁观刷新、玩家进出等）而反复重启播放。</p>
 */
public final class ClientBgm {
    /** 当前正在播放的槽位（null = 没在播）。 */
    private static CustomAudio.Slot currentSlot = null;
    /** 当前正在播放的实例（用于停止）。 */
    private static LoopingSound currentSound = null;

    private ClientBgm() {}

    /**
     * 客户端每刻调用：决定该播哪首，变了就换。
     *
     * <p>刻意**不带事件参数**：两个加载器的 tick 事件类型不同（NeoForge 的
     * {@code ClientTickEvent.Post} / Fabric 的 {@code ClientTickEvents.END_CLIENT_TICK}），
     * 而这里根本用不到事件对象——由各加载器的胶水在回调里调这个方法即可。</p>
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        CustomAudio.Slot desired = null;
        if (snap != null && !snap.sessionClosed()) {
            desired = CustomAudio.bgmFor(snap.phase(), viewerWins(snap),
                CustomAudio.minCardsLeft(snap.playerCardCounts()), rocketOnTable(snap));
        }
        // 主槽位在这个音乐包里没配文件时往下回退（王炸 → 残局 → 进行中）：
        // 否则"给老音乐包加了新 BGM 键"会变成背景音乐直接消失
        CustomAudio.Slot resolved = null;
        for (CustomAudio.Slot candidate : CustomAudio.bgmChain(desired)) {
            if (CustomSoundConfig.hasCandidates(candidate.configKey())) {
                resolved = candidate;
                break;
            }
        }

        // 目标没变就不动它——这是"不重复起播"的关键
        if (resolved == currentSlot) return;

        stop();
        if (resolved == null) return;
        SoundEvent bgmEvent = InitSounds.eventFor(resolved.configKey());
        if (bgmEvent == null) return;

        LoopingSound sound = new LoopingSound(bgmEvent, CustomSoundConfig.bgmSoundSource(), CustomSoundConfig.volume());
        mc.getSoundManager().play(sound);
        currentSound = sound;
        currentSlot = resolved;
        duckVanillaMusic();
    }

    /** 桌面最后一手是不是王炸（火箭）——火箭没人压得住，所以这个状态会持续到下一轮重新出牌。 */
    private static boolean rocketOnTable(GameStatePayload snap) {
        return snap.lastPlayedType() == com.jokernan.craftycards.game.DDZCardType.ROCKET
            && !snap.lastPlayedCards().isEmpty();
    }

    /** 停止播放并清空状态（离开牌局、退出世界时调用）。 */
    public static void stop() {
        if (currentSound != null) {
            Minecraft.getInstance().getSoundManager().stop(currentSound);
            currentSound = null;
        }
        currentSlot = null;
        restoreVanillaMusic();
    }

    /** 我们播放 BGM 之前原版"音乐"声道的音量；null = 当前没有压低。 */
    private static Double duckedFrom = null;

    /**
     * 播放 BGM 时把原版背景音乐压到 {@link CustomSoundConfig#vanillaMusicDuck()}。
     *
     * <p>BGM 自己不能落在 {@code music} 声道上，否则压低原版音乐会把 BGM 一起压掉——
     * 这也是配置默认声道是 {@code players} 的原因（见 {@link CustomSoundConfig}）。
     * 玩家若已把原版音乐调得比目标更低，就不动他的设置。</p>
     */
    private static void duckVanillaMusic() {
        if (duckedFrom != null || !CustomSoundConfig.duckVanillaMusic()) return;
        if (CustomSoundConfig.bgmSoundSource() == SoundSource.MUSIC) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        OptionInstance<Double> option = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        double target = CustomSoundConfig.vanillaMusicDuck();
        double current = option.get();
        if (current <= target) return;

        duckedFrom = current;
        option.set(target);
    }

    /**
     * 还原原版背景音乐音量。
     *
     * <p>若玩家在我们压低期间自己动过"音乐"滑块，就尊重他的选择、不覆盖——
     * 否则会出现"我明明调过音量，一退牌局又变回去了"。</p>
     */
    private static void restoreVanillaMusic() {
        if (duckedFrom == null) return;
        double from = duckedFrom;
        duckedFrom = null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        OptionInstance<Double> option = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
        if (Math.abs(option.get() - (double) CustomSoundConfig.vanillaMusicDuck()) > 1.0E-4) return;
        option.set(from);
    }

    /** 判断当前玩家是否获胜（与结算界面同一套判定）。 */
    private static boolean viewerWins(GameStatePayload snap) {
        if (snap.winnerTeam() >= 0) return snap.myIndex() == snap.landlordIndex();
        return snap.myIndex() != snap.landlordIndex();
    }

    /** 循环播放的音效实例：跟随听者、不做 3D 定位。 */
    private static final class LoopingSound extends AbstractSoundInstance {
        LoopingSound(SoundEvent event, SoundSource source, float volume) {
            super(event, source, RandomSource.create());
            this.volume = volume;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return true;
        }
    }
}
