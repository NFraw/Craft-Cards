package com.jokernan.craftycards.smoke;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "crafty_cards_smoke", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgePocClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> new PocClient().initialize(callback ->
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post tick) -> callback.accept(Minecraft.getInstance()))));
    }
}
