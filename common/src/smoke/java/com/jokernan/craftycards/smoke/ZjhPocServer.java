package com.jokernan.craftycards.smoke;

import com.jokernan.craftycards.blockentity.ZJHTableBlockEntity;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.game.server.zjh.ZJHTableManager;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.function.Consumer;

/** Real network fixture; only prepares the table, never drives the game engine. */
public final class ZjhPocServer {
    private int ticks, finishAt;
    private boolean playing;
    public void initialize(Consumer<Consumer<MinecraftServer>> start, Consumer<Consumer<ServerPlayer>> join,
                           Consumer<Consumer<MinecraftServer>> tick) {
        start.accept(server -> {
            ServerGameConfig.chipsRequired=true;
            ServerGameConfig.chipItems=java.util.List.of("minecraft:emerald");
            server.overworld().setDayTime(1000);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
                server.overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,64,z),Blocks.STONE.defaultBlockState());
                for(int y=65;y<70;y++)server.overworld().setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            server.overworld().setBlockAndUpdate(PocServer.TABLE,InitItems.ZJH_TABLE.get().defaultBlockState());
            server.overworld().setBlockAndUpdate(PocServer.TABLE.offset(2,0,0),InitItems.DDZ_TABLE.get().defaultBlockState());
            var be=(ZJHTableBlockEntity)server.overworld().getBlockEntity(PocServer.TABLE);
            var config=be.config();config.putString("chip","minecraft:emerald");config.putInt("cap",64);
            config.putInt("minimum",3);config.putInt("timeout",0);config.putBoolean("chips",true);be.configure(config);
        });
        join.accept(player -> {
            int n=Integer.parseInt(player.getScoreboardName().substring(5));
            double[][] seats={{.5,3},{-2,.5},{3,.5}};
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(player.server.overworld(),seats[n-1][0],65,seats[n-1][1],0,25);
            player.getInventory().clearContent();player.getInventory().setItem(8,new ItemStack(Items.EMERALD,64));
            player.inventoryMenu.broadcastChanges();
        });
        tick.accept(server -> {
            ticks++;
            var session=ZJHTableManager.getInstance().session(new TableKey(Level.OVERWORLD,PocServer.TABLE));
            if(session!=null&&session.engine()!=null)playing=true;
            if(playing&&session==null&&finishAt==0) {
                var players=server.getPlayerList().getPlayers();
                int total=players.stream().mapToInt(p->p.getInventory().countItem(Items.EMERALD)).sum();
                if(players.size()!=3||total!=192)throw new AssertionError("ZJH settlement conservation: "+total);
                result("PASS: ZJH real 3-player network settlement; table released; emerald total=192");finishAt=ticks+400;
            }
            if(finishAt>0&&ticks>=finishAt)server.halt(false);
            if(ticks>12000){result("FAIL: ZJH timeout");server.halt(false);}
        });
    }
    private static void result(String message){try{Files.writeString(Path.of("poc-result.txt"),message);}catch(Exception e){throw new RuntimeException(e);}}
}
