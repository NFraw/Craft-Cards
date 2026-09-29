package com.jokernan.craftycards.smoke;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class FabricPocServer implements ModInitializer {
    public void onInitialize() {
        new PocServer().initialize(
            callback -> ServerLifecycleEvents.SERVER_STARTED.register(callback::accept),
            callback -> ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(handler.getPlayer())),
            callback -> ServerTickEvents.END_SERVER_TICK.register(callback::accept));
    }
}
