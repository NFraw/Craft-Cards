package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.platform.RegHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;

/**
 * 音频事件的 **NeoForge 注册**。
 *
 * <p>common 的 {@link InitSounds} 只声明"有哪些键、注册 id 是什么"（把手表由
 * {@link CustomAudio.Slot} 枚举循环生成），登记动作在这里：遍历把手表逐个
 * {@code DeferredRegister.register} 并 bind 回去。Fabric 侧对应的是同一张表 +
 * {@code Registry.register}——两边**不会各写一份键表**。</p>
 */
public final class NeoForgeSounds {
    /** 音效事件注册表。 */
    private static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, CCReference.MOD_ID);

    private NeoForgeSounds() {}

    /** 在模组构造函数里调用。 */
    public static void register(IEventBus bus) {
        for (Map.Entry<String, RegHolder<SoundEvent>> entry : InitSounds.holders().entrySet()) {
            // 注册 id 的唯一来源：CustomAudio.soundId（生成资源包写 sounds.json 顶层键用的也是它）
            String id = CustomAudio.soundId(entry.getKey());
            entry.getValue().bind(SOUNDS.register(id,
                () -> SoundEvent.createVariableRangeEvent(CCReference.location(id))));
        }
        SOUNDS.register(bus);
    }
}
