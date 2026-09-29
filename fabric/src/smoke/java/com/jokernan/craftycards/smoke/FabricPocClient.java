package com.jokernan.craftycards.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricPocClient implements ClientModInitializer {
    public void onInitializeClient() {
        new PocClient().initialize(callback -> ClientTickEvents.END_CLIENT_TICK.register(callback::accept));
    }
}
