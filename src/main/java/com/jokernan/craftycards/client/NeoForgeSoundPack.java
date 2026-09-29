package com.jokernan.craftycards.client;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * NeoForge 侧的音频资源包注入：把（common 里生成的）音频包注册为一个**始终启用**的资源包来源。
 *
 * <p>与 {@link CustomSoundPack} 的分工：那边只负责"生成目录 + 构造 Pack"（纯 common、纯原版 API），
 * 这边只负责"用这个加载器的机制把它交给游戏"。
 * Fabric 没有等价的打包来源事件，只能在 {@code PackRepository} 构造时往来源集合里加一个
 * （见 {@code fabric/.../mixin/PackRepositoryMixin}）。</p>
 */
@EventBusSubscriber(modid = com.jokernan.craftycards.CCReference.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeSoundPack {
    private NeoForgeSoundPack() {}

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Pack pack = CustomSoundPack.buildPack();
        if (pack == null) return; // 配置里关掉了音频：不注入任何东西
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
