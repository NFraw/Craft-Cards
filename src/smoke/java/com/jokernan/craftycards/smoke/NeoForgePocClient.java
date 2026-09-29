package com.jokernan.craftycards.smoke;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "crafty_cards_smoke", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgePocClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> new PocClient().initialize(callback ->
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> callback.accept(Minecraft.getInstance()))));
    }
}
