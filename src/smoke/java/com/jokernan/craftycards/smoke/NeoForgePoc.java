package com.jokernan.craftycards.smoke;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;

@Mod("crafty_cards_smoke")
public final class NeoForgePoc {
    public NeoForgePoc() {
        var bus = NeoForge.EVENT_BUS;
        new PocServer().initialize(
            callback -> bus.addListener((ServerStartedEvent event) -> callback.accept(event.getServer())),
            callback -> bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof ServerPlayer player) callback.accept(player);
            }),
            callback -> bus.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer())));
    }
}
