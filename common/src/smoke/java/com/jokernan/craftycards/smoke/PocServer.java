package com.jokernan.craftycards.smoke;

import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test fixture only: prepares terrain/players, never drives the server engine. */
public final class PocServer {
    public static final BlockPos TABLE = new BlockPos(0, 65, 0);
    private boolean playing;
    private int tick;
    private int finishAt;
    private int allJoinedAt;

    public void initialize(java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.MinecraftServer>> registerStart, java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.level.ServerPlayer>> registerJoin, java.util.function.Consumer<java.util.function.Consumer<net.minecraft.server.MinecraftServer>> registerTick) {
        registerStart.accept(server -> {
            ServerGameConfig.chipsRequired = true;
            ServerGameConfig.turnTimeoutTicks = 600;
            ServerGameConfig.participantsSeeFaces = false;
            server.overworld().setDayTime(1000);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                server.overworld().setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                for (int y = 65; y < 70; y++)
                    server.overworld().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            if (server.getPackRepository().getAvailableIds().contains("crafty_cards/custom_sounds"))
                throw new AssertionError("Client audio pack leaked into server data packs");
            var serializer = com.jokernan.craftycards.entity.data.CCDataSerializers.STACK.get();
            if (net.minecraft.network.syncher.EntityDataSerializers.getSerializedId(serializer) < 0)
                throw new AssertionError("Card entity serializer not registered");
        });
        registerJoin.accept(p -> {
            var server = p.getServer();
            int n = Integer.parseInt(p.getGameProfile().getName().substring(5));
            p.setGameMode(GameType.CREATIVE);
            double[][] seats = {{0.5, 3.0}, {-2.0, 0.5}, {3.0, 0.5}};
            p.teleportTo(server.overworld(), seats[n-1][0], 65, seats[n-1][1], 0, 25);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.STICK));
            if (n == 1) p.getInventory().setItem(1, new ItemStack(InitItems.DDZ_TABLE_ITEM.get()));
            p.getInventory().setItem(8, new ItemStack(Items.EMERALD, 64));
            p.inventoryMenu.broadcastChanges();
        });
        registerTick.accept(server -> {
            tick++;
            if (allJoinedAt == 0 && server.getPlayerList().getPlayers().size() == 3) allJoinedAt = tick;
            var tableState = server.overworld().getBlockState(TABLE);
            if (tableState.is(InitItems.DDZ_TABLE.get())
                && !(server.overworld().getBlockEntity(TABLE) instanceof com.jokernan.craftycards.blockentity.DDZTableBlockEntity)) {
                result("FAIL: placed table has no block entity on dedicated server"); server.halt(false); return;
            }
            if (allJoinedAt > 0 && tick > allJoinedAt + 600 && !tableState.is(InitItems.DDZ_TABLE.get())) {
                result("FAIL: client placement never reached dedicated server"); server.halt(false); return;
            }
            var session = DDZTableManager.getInstance().session(new TableKey(Level.OVERWORLD, TABLE));
            if (session != null && session.phase() == DDZGamePhase.PLAYING) playing = true;
            if (playing && finishAt == 0 && (session == null || session.phase() == DDZGamePhase.SETTLED)) {
                var players = server.getPlayerList().getPlayers();
                if (players.size() != 3) throw new AssertionError("Lost a real client before settlement");
                int total = players.stream().mapToInt(p -> p.getInventory().countItem(Items.EMERALD)).sum();
                if (total != 192) throw new AssertionError("Chips not conserved: " + total);
                if (players.stream().anyMatch(p -> p.getInventory().countItem(Items.EMERALD) == 64))
                    throw new AssertionError("Settlement did not transfer chips");
                result("PASS: three real players, session ended, emerald total=192; clients verify normal settlement");
                finishAt = tick + 400;
            }
            if (finishAt > 0 && tick >= finishAt) server.halt(false);
            if (tick > 12000) { result("FAIL: scenario timed out"); server.halt(false); }
        });
    }

    private static void result(String text) {
        try { Files.writeString(Path.of("poc-result.txt"), text); }
        catch (Exception e) { throw new RuntimeException(e); }
        System.out.println("POC " + text);
    }
}

