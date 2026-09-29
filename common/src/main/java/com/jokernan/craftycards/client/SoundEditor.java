package com.jokernan.craftycards.client;
import com.jokernan.craftycards.platform.SoundPackInjection;

import net.minecraft.client.Minecraft;

/**
 * 音频配置的"编辑会话"：设置主页与音乐包页共用一份工作副本。
 *
 * <p>为什么要共用：双方写的是同一个 {@code sounds.json}。如果各页各存一份工作副本，
 * "在音乐包页选了包、没保存就回主页、在主页点了什么"会把选包静默丢掉。
 * 所以两边读写同一个会话，任何一处点「保存并应用」都是落地全部改动。</p>
 *
 * <p>会话内容只有三项：**总开关、音量、当前音乐包**。音频的内容（哪个键有哪些文件、
 * 各占多少权重）完全来自音乐包，游戏侧只读——想改音频请用制作程序改包。</p>
 *
 * <p>生命周期：进入设置主页时 {@link #begin()}（已有会话则保留，来回切页不丢改动），
 * 关闭整个设置界面时 {@link #discard()}（有未保存改动则先弹确认，见
 * {@link UnsavedAudioScreen}）。</p>
 *
 * <p>保存是"重活"：要重新生成注入资源包并触发资源重载，音频才会真正被游戏加载。
 * 所以不做"改一下存一下"的写穿，而是攒到点「保存并应用」——这也是共用会话的原因。</p>
 */
final class SoundEditor {
    /** 工作副本；不存在"没有会话"的状态，会话从设置主页进入时开始。 */
    private static boolean active;
    private static boolean enabled;
    private static float volume;
    private static String pack;

    private SoundEditor() {}

    /** 开始编辑（已有会话则保留）。 */
    static void begin() {
        if (active) return;
        active = true;
        enabled = CustomSoundConfig.enabledValue();
        volume = CustomSoundConfig.volumeValue();
        pack = CustomSoundConfig.activePackId();
    }

    /** 结束会话（保存后、或放弃改动时调用）。 */
    static void discard() {
        active = false;
    }

    static boolean enabled() {
        begin();
        return enabled;
    }

    static void enabled(boolean value) {
        begin();
        enabled = value;
    }

    static float volume() {
        begin();
        return volume;
    }

    static void volume(float value) {
        begin();
        volume = Math.max(0F, Math.min(1F, value));
    }

    static String pack() {
        begin();
        return pack;
    }

    static void pack(String value) {
        begin();
        pack = value == null ? "" : value;
    }

    /** 当前 BGM 声道的中文名（提示"听不到"时用；这项只能在 sounds.json 里改）。 */
    static String bgmLabel() {
        return CustomSoundConfig.bgmCategoryLabel();
    }

    // === 脏检查与落地 ===

    /** 是否有未保存的改动（与已经写进配置文件的值比较）。 */
    static boolean dirty() {
        begin();
        return enabled != CustomSoundConfig.enabledValue()
            || Math.abs(volume - CustomSoundConfig.volumeValue()) > 1.0E-4F
            || !pack.equals(CustomSoundConfig.activePackId());
    }

    /**
     * 落地：写配置 → 重建注入资源包 → 触发资源重载。
     *
     * <p>"重建 + 重载"这一步绕不开：音频必须经资源包被游戏加载，
     * 保存后不重载就只是文件变了、游戏里还是旧声音。</p>
     */
    static void commit() {
        begin();
        CustomSoundConfig.applySelection(enabled, volume, pack);
        SoundPackInjection.regenerate();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.reloadResourcePacks();
    }
}
