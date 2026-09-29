package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.platform.RegHolder;
import net.minecraft.sounds.SoundEvent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 音频键 → 音效事件的**把手**（7 个音效 + 6 个 BGM + 48 条牌型语音）。
 *
 * <p>注册动作本身在加载器侧（NeoForge 的 {@code init/NeoForgeSounds}、Fabric 的对应类）：
 * 它们遍历 {@link #holders()} 逐个 {@code Registry.register} 再 bind 回来。
 * 于是 common 只负责"有哪些键、每个键的注册 id 是什么"，两个加载器不会各写一份键表。</p>
 *
 * <p><b>加音频槽位现在只需改一处</b>：往 {@link CustomAudio.Slot} 加一个枚举——把手表由枚举循环生成。
 * 历史教训：这里以前是一串手写字段，加 {@code BGM_CLUTCH} / {@code BGM_ROCKET} 时忘了在那一行
 * 补注册，结果那两首 BGM **永远不响**（完全静默，连"缺音频"的日志都不会有，因为事件压根不存在），
 * 靠 {@code CCAudioGameTest.soundEventIdsMatchSoundId} 遍历 61 个键核对注册表才抓到。</p>
 *
 * <p><b>模组不打包任何音频文件</b>，因此这些 SoundEvent 默认**没有音频资源**：音频由玩家安装的
 * 音乐包提供，模组把选中的文件生成为一个高优先级资源包（见 {@code client/CustomSoundPack}）
 * 并在那里给这些 id 配上文件。某个键没有文件时，音效回退到原版音效、BGM 不播
 * ——见 {@code client/ClientSounds} 与 {@code CustomAudio#bgmChain}。</p>
 */
public final class InitSounds {
    /** 槽位 → 把手（同一个把手实例也放在 {@link #BY_KEY} 里）。 */
    private static final Map<CustomAudio.Slot, RegHolder<SoundEvent>> BY_SLOT =
        new EnumMap<>(CustomAudio.Slot.class);

    /** 配置键 → 把手（槽位与牌型语音共用一个查找表，顺序稳定便于自检输出）。 */
    private static final Map<String, RegHolder<SoundEvent>> BY_KEY = new LinkedHashMap<>();

    static {
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            RegHolder<SoundEvent> holder = RegHolder.create(CustomAudio.soundId(slot.configKey()));
            BY_SLOT.put(slot, holder);
            BY_KEY.put(slot.configKey(), holder);
        }
        for (String voice : CustomAudio.VOICE_KEYS) {
            // 注册 id 与"生成资源包时写的顶层键"必须同源：都取 CustomAudio.soundId（语音带 voice_ 前缀）。
            // 这里曾经硬写 "voice_" + voice 而生成侧写裸键，结果 48 条语音全部静音
            BY_KEY.put(voice, RegHolder.create(CustomAudio.soundId(voice)));
        }
    }

    private InitSounds() {}

    /**
     * 需要注册的全部音频事件：配置键 → 把手。
     *
     * <p>加载器在初始化时遍历它，用 {@link CustomAudio#soundId(String)} 算出注册 id、注册后 bind 回来。</p>
     */
    public static Map<String, RegHolder<SoundEvent>> holders() {
        return Collections.unmodifiableMap(BY_KEY);
    }

    /**
     * 取某个音频键的音效事件（槽位键与牌型语音键都走这里）；键不认识时返回 null。
     *
     * <p>没有音频资源的键也能取到事件，但播放会静默（原版音效库找不到资源），
     * 所以调用方必须先确认该键有候选文件（{@code CustomSoundConfig.hasCandidates}）。</p>
     */
    public static SoundEvent eventFor(String key) {
        RegHolder<SoundEvent> holder = BY_KEY.get(key);
        return holder == null ? null : holder.get();
    }

    /** 取某个槽位对应的音效事件；槽位未注册时返回 null。 */
    public static SoundEvent event(CustomAudio.Slot slot) {
        RegHolder<SoundEvent> holder = BY_SLOT.get(slot);
        return holder == null ? null : holder.get();
    }

    /**
     * 取"试听某一个具体文件"的音效事件。
     *
     * <p>用 {@link SoundEvent#createVariableRangeEvent} 现场造一个未注册的事件，
     * id 指向生成资源包里的 {@code preview/<键>_<序号>} 条目。这样界面能精确试听某一候选文件，
     * 而不是碰运气听该键随机挑中的那一条。</p>
     */
    public static SoundEvent previewEvent(String key, int index) {
        return SoundEvent.createVariableRangeEvent(
            CCReference.location(CustomAudio.previewKey(key, index)));
    }

    /** 某音频键是否已登记（牌型语音与槽位都在内）。 */
    public static boolean isRegistered(String key) {
        return BY_KEY.containsKey(key);
    }
}
