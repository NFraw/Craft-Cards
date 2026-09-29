package com.jokernan.craftycards.smoke;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;

@Mod("crafty_cards_smoke")
public final class ForgePoc {
    public ForgePoc() {
        var bus = MinecraftForge.EVENT_BUS;
        // Forge 51 can leave a non-daemon scheduled worker alive after the
        // server thread has saved and stopped. Only this development harness
        // owns the JVM: finish it after joining the server, preserving FAIL.
        bus.addListener((ServerStoppedEvent event) -> {
            Thread serverThread = Thread.currentThread();
            Thread.ofPlatform().daemon().name("poc-server-exit").start(() -> {
                try {
                    serverThread.join();
                    String result = java.nio.file.Files.readString(java.nio.file.Path.of("poc-result.txt"));
                    System.exit(result.startsWith("PASS:") ? 0 : 1);
                } catch (Exception failure) {
                    failure.printStackTrace();
                    System.exit(1);
                }
            });
        });
        new PocServer().initialize(
            callback -> bus.addListener((ServerStartedEvent event) -> callback.accept(event.getServer())),
            callback -> bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof ServerPlayer player) callback.accept(player);
            }),
            callback -> bus.addListener((TickEvent.ServerTickEvent.Post event) -> callback.accept(event.getServer())));
    }
}
