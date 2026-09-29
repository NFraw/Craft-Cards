package com.jokernan.craftycards.forge;

import com.jokernan.craftycards.game.server.DDZTableManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ForgeTableEvents {
    public static void register(IEventBus bus) {
        bus.addListener((TickEvent.ServerTickEvent.Post event) ->
            DDZTableManager.getInstance().tick(event.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player)
                DDZTableManager.getInstance().handleLogout(player);
        });
        bus.addListener((BlockEvent.BreakEvent event) -> {
            if (!event.isCanceled() && event.getLevel() instanceof Level level)
                DDZTableManager.getInstance().handleBlockBreak(level, event.getPos(), event.getState());
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (DDZTableManager.shouldForceBlockUse(event.getEntity(), event.getLevel().getBlockState(event.getPos())))
                event.setUseBlock(Event.Result.ALLOW);
        });
    }
}
