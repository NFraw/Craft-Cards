package com.jokernan.craftycards.game.server.zjh;

import com.jokernan.craftycards.blockentity.ZJHTableBlockEntity;
import com.jokernan.craftycards.game.server.*;
import com.jokernan.craftycards.network.payload.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

/** Main-thread session registry; never creates a session for an arbitrary position. */
public final class ZJHTableManager {
    private static final ZJHTableManager INSTANCE=new ZJHTableManager();
    private final Map<TableKey,ZJHSession> sessions=new HashMap<>();
    private MinecraftServer server;
    public static ZJHTableManager getInstance(){return INSTANCE;}
    public void initialize(MinecraftServer s) {
        if(server==s)return;sessions.clear();server=s;ZJHChipEscrow.get(s).recover();
    }
    public void handle(ServerPlayer player,ZJHActionPayload payload) {
        initialize(player.server);ZJHChipEscrow.get(server).deliver(player);
        try {
            if(!player.serverLevel().dimension().equals(payload.table().dimension()))throw new IllegalArgumentException("不能操作其他维度的牌桌");
            if(!player.serverLevel().hasChunkAt(payload.table().pos())
                ||!(player.serverLevel().getBlockEntity(payload.table().pos()) instanceof ZJHTableBlockEntity be))
                throw new IllegalArgumentException("炸金花牌桌不存在");
            double radius=Math.max(1,ServerGameConfig.joinRadius);
            if(payload.action()!=ZJHActionPayload.Action.LEAVE&&payload.action()!=ZJHActionPayload.Action.CLOSE
                && player.distanceToSqr(payload.table().pos().getX()+.5,payload.table().pos().getY()+.5,payload.table().pos().getZ()+.5)>radius*radius)
                throw new IllegalArgumentException("离牌桌太远，请靠近后操作");
            if(payload.action()==ZJHActionPayload.Action.JOIN) {
                if(DDZTableManager.getInstance().contains(player)||participatesElsewhere(player,payload.table()))
                    throw new IllegalArgumentException("请先离开当前牌局");
                for(ZJHSession other:sessions.values())if(!other.key().equals(payload.table()))other.unwatch(player);
            }
            if((payload.action()==ZJHActionPayload.Action.CONFIG||payload.action()==ZJHActionPayload.Action.WATCH)
                &&(DDZTableManager.getInstance().contains(player)||participatesElsewhere(player,payload.table())))
                throw new IllegalArgumentException("请先离开当前牌局");
            if(payload.action()==ZJHActionPayload.Action.CONFIG||payload.action()==ZJHActionPayload.Action.WATCH)
                for(ZJHSession other:sessions.values())if(!other.key().equals(payload.table()))other.unwatch(player);
            ZJHSession session=sessions.get(payload.table());
            if(session==null) {
                if(payload.action()!=ZJHActionPayload.Action.JOIN&&payload.action()!=ZJHActionPayload.Action.CONFIG
                    &&payload.action()!=ZJHActionPayload.Action.WATCH)throw new IllegalArgumentException("本桌还没有牌局");
                session=new ZJHSession(payload.table(),player.serverLevel(),be);sessions.put(payload.table(),session);
            }
            session.handle(player,payload);
            // The final result was sent by handle(). Release the table without sending a close packet over that result.
            if(session.finished())sessions.remove(payload.table(),session);
        } catch(IllegalArgumentException error) {
            player.sendSystemMessage(Component.literal(error.getMessage()));
            ZJHSession s=sessions.get(payload.table());
            if(s!=null&&s.canView(player))s.reply(player,error.getMessage(),payload.sequence());
            else {
                var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("closed",true);tag.putString("feedback",error.getMessage());
                try {com.jokernan.craftycards.platform.Network.sendZjh(player,new ZJHStatePayload(payload.table(),ZJHActionPayload.NONE,0,payload.sequence(),tag));}
                catch(RuntimeException sendError){com.jokernan.craftycards.CCReference.LOG.warn("炸金花拒绝响应发送失败: {}",sendError.toString());}
            }
        }
    }
    private boolean participatesElsewhere(ServerPlayer player,TableKey key) {
        return sessions.values().stream().anyMatch(s->!s.key().equals(key)&&s.contains(player));
    }
    public boolean contains(ServerPlayer p){return sessions.values().stream().anyMatch(s->s.contains(p));}
    public ZJHSession session(TableKey key){return sessions.get(key);}
    public void logout(ServerPlayer p){for(ZJHSession s:List.copyOf(sessions.values())){
        s.logout(p);if(s.finished())sessions.remove(s.key(),s);
    }}
    public void removed(Level level,BlockPos pos) {
        var key=new TableKey(level.dimension(),pos);ZJHSession s=sessions.remove(key);
        if(s!=null)s.cancel("牌桌已拆除，本局投入已退还");
    }
    public void tick(MinecraftServer s) {
        initialize(s);if(s.getTickCount()%5!=0)return;
        for(ZJHSession session:List.copyOf(sessions.values())) {
            if(session.tick())sessions.remove(session.key());
        }
        if(s.getTickCount()%20==0)for(ServerPlayer p:s.getPlayerList().getPlayers())ZJHChipEscrow.get(s).deliver(p);
    }
    public void stop(MinecraftServer s) {
        for(ZJHSession session:List.copyOf(sessions.values()))session.cancel("服务器关闭，本局投入已退还");
        sessions.clear();for(ServerPlayer p:s.getPlayerList().getPlayers())ZJHChipEscrow.get(s).deliver(p);
        server=null;
    }
}
