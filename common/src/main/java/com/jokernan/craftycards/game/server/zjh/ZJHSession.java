package com.jokernan.craftycards.game.server.zjh;

import com.jokernan.craftycards.blockentity.ZJHTableBlockEntity;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.game.zjh.*;
import com.jokernan.craftycards.network.payload.*;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import java.util.*;

/** One hand, retaining its complete participant history for the final result snapshot. */
public final class ZJHSession {
    private static final class Member {
        final UUID id;final String name;ServerPlayer player;boolean present=true;String recent="已加入，等待开局";
        Member(ServerPlayer p){id=p.getUUID();name=p.getScoreboardName();player=p;}
    }
    private final TableKey key;
    private final ServerLevel level;
    private final ZJHTableBlockEntity table;
    private final UUID id=UUID.randomUUID();
    private final List<Member> members=new ArrayList<>();
    private final Set<UUID> watchers=new HashSet<>();
    private final Map<UUID,Long> sequences=new HashMap<>();
    private ZJHEngine engine;
    private long version,turnStart,lastActivity,lockStart,handNumber;
    private UUID locker,escrowId;
    private boolean physical,paidOut,closed;
    private int nextDealer=-1;
    private String waitingReason="等待玩家加入";
    public ZJHSession(TableKey key,ServerLevel level,ZJHTableBlockEntity table) {
        this.key=key;this.level=level;this.table=table;lastActivity=now();
    }
    public TableKey key(){return key;}
    public ZJHEngine engine(){return engine;}
    private long now(){return level.getServer().getTickCount();}
    private ZJHChipEscrow escrow(){return ZJHChipEscrow.get(level.getServer());}
    private ServerPlayer online(UUID uuid){
        ServerPlayer real=level.getServer().getPlayerList().getPlayer(uuid);if(real!=null)return real;
        return members.stream().filter(m->m.id.equals(uuid)&&m.present).map(m->m.player).filter(Objects::nonNull).findFirst().orElse(null);
    }
    private int seat(UUID uuid){for(int i=0;i<members.size();i++)if(members.get(i).id.equals(uuid))return i;return -1;}
    public boolean finished(){return engine!=null&&engine.settled()&&paidOut;}
    public boolean contains(ServerPlayer p){int i=seat(p.getUUID());return !finished()&&i>=0&&members.get(i).present;}
    public boolean canView(ServerPlayer p){return contains(p)||watchers.contains(p.getUUID())||p.getUUID().equals(locker);}
    private boolean playing(){return engine!=null&&!engine.settled();}
    private boolean near(ServerPlayer p) {
        double r=Math.max(1,ServerGameConfig.joinRadius);
        return p.serverLevel()==level&&p.distanceToSqr(key.pos().getX()+.5,key.pos().getY()+.5,key.pos().getZ()+.5)<=r*r;
    }
    public void handle(ServerPlayer p,ZJHActionPayload a) {
        if(closed)throw new IllegalArgumentException("牌局已关闭，请重新加入");
        if(a.sequence()<=sequences.getOrDefault(p.getUUID(),-1L)){reply(p,"",a.sequence());return;}
        sequences.put(p.getUUID(),a.sequence());
        boolean initial=a.action()==ZJHActionPayload.Action.JOIN||a.action()==ZJHActionPayload.Action.CONFIG
            ||a.action()==ZJHActionPayload.Action.WATCH;
        boolean terminal=a.action()==ZJHActionPayload.Action.LEAVE||a.action()==ZJHActionPayload.Action.CLOSE;
        if(!initial&&(!id.equals(a.session())||(!terminal&&a.version()!=version))) {
            com.jokernan.craftycards.CCReference.LOG.debug("炸金花拒绝陈旧操作: player={}, action={}, hand={}, clientVersion={}, serverVersion={}",
                p.getScoreboardName(),a.action(),handNumber,a.version(),version);
            reply(p,"状态已更新，请重新确认操作",a.sequence());return;
        }
        int seat=seat(p.getUUID());
        boolean heldConfig=p.getUUID().equals(locker);
        switch(a.action()) {
            case JOIN -> {
                if(!table.configured)throw new IllegalArgumentException("本桌尚未配置，请先 Shift+右键配置并保存");
                if(playing())throw new IllegalArgumentException("本局已经开始，可 Shift+右键观战");
                if(locker!=null)throw new IllegalArgumentException("牌桌配置中，请稍候");
                if(contains(p))throw new IllegalArgumentException("你已经加入，人数足够后自动开局");
                if(members.stream().filter(m->m.present).count()>=6)throw new IllegalArgumentException("本桌已满六人");
                if(ServerGameConfig.chipsRequired&&table.chips&&table.chip.isEmpty())throw new IllegalArgumentException("本桌筹码还没配，请 Shift+右键配置");
                if(ServerGameConfig.chipsRequired&&table.chips&&ZJHChipEscrow.count(p,chip())<table.cap)
                    throw new IllegalArgumentException("需要备齐本局预算 "+table.cap+" 个筹码");
                if(engine!=null)throw new IllegalArgumentException("本局已结束，请重新加入");
                members.add(new Member(p));watchers.remove(p.getUUID());
            }
            case CONFIG -> {
                if(contains(p)){leave(p);return;}
                if(playing()) {watch(p);return;}
                // 与斗地主一致：空桌时任何附近玩家都能配置，只靠一把临时编辑锁防冲突。
                // 原先"非房主一律转观战"会让人以为自己被卡住了（右键配置却变成看客）。
                if(locker!=null&&!locker.equals(p.getUUID()))throw new IllegalArgumentException("已有玩家正在配置");
                locker=p.getUUID();lockStart=now();
            }
            case SAVE -> {
                if(!p.getUUID().equals(locker)||playing())throw new IllegalArgumentException("没有配置权限或锁已失效");
                String chip=a.config().getString("chip");
                if(!chip.isEmpty()&&(!ServerGameConfig.chipItems.contains(chip)||resolve(chip)==Items.AIR))
                    throw new IllegalArgumentException("筹码不在服务器白名单中");
                table.configure(a.config());locker=null;
            }
            case CLOSE -> {if(p.getUUID().equals(locker))locker=null;}
            case WATCH -> {watch(p);return;}
            case LEAVE -> {leave(p);return;}
            // Keep legacy enum ordinals, but clients can no longer force a start or reset another player's result.
            case READY, START, LOBBY -> throw new IllegalArgumentException("加入即就绪，人数足够自动开局；结算后自动退局，新局请重新加入");
            case REVEAL, CALL, RAISE, COMPARE, FOLD -> {
                requireMember(seat,p);
                if(!playing()||engine.current()!=seat||!engine.active(seat))throw new IllegalArgumentException("还没轮到你行动");
                long[] before=new long[members.size()];for(int i=0;i<before.length;i++)before[i]=engine.contribution(i);
                switch(a.action()) {
                    case REVEAL -> {engine.reveal(seat,a.value());members.get(seat).recent="翻牌 "+engine.revealedCount(seat)+"/3";}
                    case CALL -> {engine.call(seat);members.get(seat).recent="跟注 "+(engine.contribution(seat)-before[seat]);}
                    case RAISE -> {engine.raise(seat,a.value());members.get(seat).recent="加注 "+(engine.contribution(seat)-before[seat]);}
                    case COMPARE -> {
                        engine.compare(seat,a.target());members.get(seat).recent="与 "+members.get(a.target()).name+" 比牌";
                        if(!engine.active(seat))members.get(seat).recent="比牌失败";
                        if(!engine.active(a.target()))members.get(a.target()).recent="比牌失败";
                    }
                    case FOLD -> {engine.fold(seat);members.get(seat).recent="弃牌";}
                    default -> throw new AssertionError();
                }
                reconcile(before);
                if(a.action()!=ZJHActionPayload.Action.REVEAL)turnStart=now();
                settleIfNeeded();
                com.jokernan.craftycards.CCReference.LOG.info("炸金花行动: table={}, hand={}, actor={}, seat={}, action={}, next={}, round={}, active={}",
                    key,handNumber,p.getScoreboardName(),seat,a.action(),engine.settled()?"结算":members.get(engine.current()).name,
                    engine.round(),java.util.stream.IntStream.range(0,members.size()).filter(engine::active).mapToObj(i->members.get(i).name).toList());
            }
        }
        tryAutoStart();version++;lastActivity=now();broadcast("");
        // An editor need not join or spectate. A released lock must still close that editor's UI and acknowledge SAVE/CLOSE.
        if(heldConfig&&!p.getUUID().equals(locker)&&!canView(p))send(p,"",true);
    }
    private void requireMember(int seat,ServerPlayer p) {
        if(seat<0||!members.get(seat).present)throw new IllegalArgumentException("你没有参加这局");
    }
    private void watch(ServerPlayer p) {
        if(contains(p))throw new IllegalArgumentException("你已经在本桌参局");
        if(watchers.remove(p.getUUID())){send(p,"已停止观战",true);return;}
        watchers.add(p.getUUID());send(p,"只读观战，不公开暗牌",false);
    }
    public void unwatch(ServerPlayer p) {
        if(watchers.remove(p.getUUID()))send(p,"已切换牌桌",true);
    }
    /** Called on join/config completion and periodically when waiting players return or refill their budget. */
    private boolean tryAutoStart() {
        if(engine!=null)return false;
        if(!table.configured){waitingReason="本桌尚未配置";return false;}
        if(locker!=null){waitingReason="配置编辑中，请稍候";return false;}
        long count=members.stream().filter(m->m.present).count();
        if(count<table.minimum){waitingReason="等待玩家加入 ("+count+"/"+table.minimum+")";return false;}
        Item item=chip();boolean needsChips=ServerGameConfig.chipsRequired&&table.chips;
        if(needsChips&&(item==Items.AIR||!ServerGameConfig.chipItems.contains(table.chip))){waitingReason="本桌筹码未配置或不在白名单";return false;}
        for(Member m:members)if(m.present) {
            ServerPlayer p=online(m.id);
            if(p==null||!near(p)){waitingReason=m.name+" 离桌过远，等待返回";return false;}
            if(needsChips&&ZJHChipEscrow.count(p,item)<table.cap){waitingReason=m.name+" 筹码不足，需补足 "+table.cap;return false;}
        }
        start();waitingReason="";return true;
    }
    private void start() {
        if(playing()||locker!=null)throw new IllegalArgumentException("本桌正在游戏或配置");
        List<Member> joined=members.stream().filter(m->m.present).toList();
        if(joined.size()<table.minimum)throw new IllegalArgumentException("至少需要 "+table.minimum+" 人");
        if(joined.stream().map(m->m.id).distinct().count()!=joined.size())throw new IllegalStateException("牌桌出现重复玩家身份");
        physical=ServerGameConfig.chipsRequired&&table.chips;
        Item item=physical?chip():Items.AIR;
        if(physical&&(item==Items.AIR||!ServerGameConfig.chipItems.contains(table.chip)))throw new IllegalArgumentException("本桌筹码未配置或不在白名单");
        for(Member m:joined) {
            ServerPlayer p=online(m.id);
            if(p==null||!near(p))throw new IllegalArgumentException(m.name+" 已远离牌桌");
            if(physical&&ZJHChipEscrow.count(p,item)<table.cap)throw new IllegalArgumentException(m.name+" 筹码不足，需要 "+table.cap);
        }
        UUID newEscrow=UUID.randomUUID();
        try {if(physical)for(Member m:joined)escrow().deposit(newEscrow,online(m.id),item,table.cap);}
        catch(RuntimeException error) {
            escrow().cancel(newEscrow);for(Member m:joined)if(online(m.id)!=null)escrow().deliver(online(m.id));throw error;
        }
        members.clear();members.addAll(joined);escrowId=newEscrow;paidOut=false;
        var random=new java.security.SecureRandom();
        engine=ZJHEngine.deal(members.size(),table.ante,table.cap,nextDealer<0?random.nextInt(members.size()):Math.floorMod(nextDealer,members.size()),random);
        nextDealer=(engine.dealer()+1)%members.size();
        handNumber++;
        com.jokernan.craftycards.CCReference.LOG.info("炸金花开局: table={}, hand={}, players={}, first={}",
            key,handNumber,members.stream().map(m->m.name).toList(),members.get(engine.current()).name);
        if(physical)for(Member m:members)escrow().charge(escrowId,m.id,table.ante);
        for(Member m:members)m.recent="投入底注 "+table.ante;
        turnStart=now();settleIfNeeded();
    }
    private void reconcile(long[] before) {
        if(physical)for(int i=0;i<members.size();i++) {
            int diff=Math.toIntExact(engine.contribution(i)-before[i]);if(diff>0)escrow().charge(escrowId,members.get(i).id,diff);
        }
    }
    private void settleIfNeeded() {
        if(engine==null||!engine.settled()||paidOut)return;
        if(physical) {
            long[] payouts=new long[members.size()];for(int i=0;i<payouts.length;i++)payouts[i]=engine.payout(i);
            escrow().settle(escrowId,members.stream().map(m->m.id).toList(),payouts);
            for(Member m:members)if(online(m.id)!=null)escrow().deliver(online(m.id));
        }paidOut=true;
    }
    private void leave(ServerPlayer p) {
        int seat=seat(p.getUUID());
        if(seat>=0&&members.get(seat).present) {
            Member m=members.get(seat);m.present=false;m.player=null;m.recent="离开 / 弃牌";
            if(playing()&&engine.active(seat)) {
                boolean wasCurrent=engine.current()==seat;engine.fold(seat);
                if(physical)escrow().refundReserve(escrowId,m.id);
                if(wasCurrent)turnStart=now();settleIfNeeded();
            }
            if(engine==null)members.remove(seat);
        }
        watchers.remove(p.getUUID());if(p.getUUID().equals(locker))locker=null;
        tryAutoStart();version++;send(p,"你离开了牌局",true);escrow().deliver(p);broadcast("");lastActivity=now();
    }
    public void logout(ServerPlayer p) {
        if(contains(p)||watchers.contains(p.getUUID())||p.getUUID().equals(locker))leave(p);
    }
    public boolean tick() {
        if(closed||finished())return true;
        if(!level.hasChunkAt(key.pos())||table.isRemoved()||level.getBlockEntity(key.pos())!=table) {
            cancel("牌桌卸载，本局投入已退还");return true;
        }
        // 没人参局、没人在看、也没人握着配置锁：会话本身可以丢掉，下一次右键会新建
        if(engine==null&&members.isEmpty()&&watchers.isEmpty()&&locker==null)return true;
        boolean changed=false;UUID previousLocker=locker;
        if(locker!=null&&(online(locker)==null||!near(online(locker))||now()-lockStart>=2400)) {locker=null;changed=true;}
        for(UUID watcher:List.copyOf(watchers)) {
            ServerPlayer p=online(watcher);
            if(p==null||!near(p)){watchers.remove(watcher);if(p!=null)send(p,"已离开观战范围",true);}
        }
        if(playing()&&table.timeout>0&&now()-turnStart>=table.timeout) {
            int seat=engine.current();engine.fold(seat);members.get(seat).recent="超时弃牌";
            com.jokernan.craftycards.CCReference.LOG.info("炸金花超时弃牌: table={}, hand={}, player={}, round={}",key,handNumber,members.get(seat).name,engine.round());
            turnStart=now();settleIfNeeded();changed=true;
        }
        String previousReason=waitingReason;
        if(tryAutoStart()||!waitingReason.equals(previousReason))changed=true;
        if(changed){version++;broadcast("");}
        if(previousLocker!=null&&!previousLocker.equals(locker)) {
            ServerPlayer editor=online(previousLocker);
            if(editor!=null&&!canView(editor))send(editor,"配置编辑已结束",true);
        }
        if(finished())return true;
        if(ServerGameConfig.idleTimeoutTicks>0&&now()-lastActivity>ServerGameConfig.idleTimeoutTicks&&!playing()) {
            cancel("牌桌长时间空闲，界面已关闭");return true;
        }return false;
    }
    public void cancel(String reason) {
        if(closed)return;
        if(physical&&escrowId!=null&&!paidOut)escrow().cancel(escrowId);
        Set<UUID> receivers=recipients();closed=true;locker=null;version++;
        for(UUID uuid:receivers)if(online(uuid)!=null){send(online(uuid),reason,true);escrow().deliver(online(uuid));}
    }
    private Set<UUID> recipients() {
        Set<UUID> result=new HashSet<>(watchers);for(Member m:members)if(m.present)result.add(m.id);
        if(locker!=null)result.add(locker);return result;
    }
    private void broadcast(String message){for(UUID uuid:recipients())if(online(uuid)!=null)send(online(uuid),message,false);}
    public void reply(ServerPlayer p,String message,long sequence) {sequences.merge(p.getUUID(),sequence,Math::max);send(p,message,false);}
    private void send(ServerPlayer p,String message,boolean close) {
        try{Network.sendZjh(p,snapshotFor(p,message,close));}
        catch(RuntimeException error){com.jokernan.craftycards.CCReference.LOG.warn("向 {} 发送炸金花快照失败: {}",p.getScoreboardName(),error.toString());}
    }
    public ZJHStatePayload snapshotFor(ServerPlayer p,String message,boolean close) {
        CompoundTag t=new CompoundTag();int me=seat(p.getUUID());
        boolean revealResult=engine!=null&&engine.settled()&&(me>=0||watchers.contains(p.getUUID()));
        t.putInt("me",me>=0&&members.get(me).present?me:-1);t.putString("phase",engine==null?"WAITING":engine.settled()?"SETTLED":"PLAYING");
        t.putBoolean("closed",close||closed);t.putString("feedback",message);
        t.putBoolean("autoExited",finished());
        t.putBoolean("configOpen",p.getUUID().equals(locker));t.putString("waitingReason",waitingReason);
        t.put("config",table.config());t.putBoolean("physical",engine==null?ServerGameConfig.chipsRequired&&table.chips:physical);
        t.putBoolean("chipsAllowed",ServerGameConfig.chipsRequired);
        ListTag chips=new ListTag();for(String c:ServerGameConfig.chipItems)chips.add(StringTag.valueOf(c));t.put("chipChoices",chips);
        t.putInt("round",engine==null?0:engine.round());t.putInt("current",engine==null?-1:engine.current());
        t.putLong("handNumber",handNumber);
        t.putInt("dealer",engine==null?-1:engine.dealer());t.putInt("base",engine==null?table.ante:engine.base());
        t.putLong("pot",engine==null?0:engine.pot());
        t.putInt("remaining",playing()&&table.timeout>0?(int)Math.max(0,table.timeout-(now()-turnStart)):-1);
        ListTag list=new ListTag();
        for(int i=0;i<members.size();i++) {
            Member m=members.get(i);CompoundTag row=new CompoundTag();row.putUUID("uuid",m.id);row.putString("name",m.name);
            row.putBoolean("present",m.present);row.putString("recent",m.recent);
            row.putBoolean("active",engine==null?m.present:engine.active(i));
            row.putInt("revealed",engine==null?0:engine.revealedCount(i));
            row.putLong("paid",engine==null?0:engine.contribution(i));row.putLong("payout",engine==null?0:engine.payout(i));
            row.putBoolean("winner",engine!=null&&engine.settled()&&engine.winners().contains(i));
            if(engine!=null) {
                int[] hidden={-1,-1,-1},hand=engine.hand(i);
                for(int c=0;c<3;c++)if(revealResult||(i==me&&(engine.revealedMask(i)&(1<<c))!=0))hidden[c]=hand[c];
                row.putIntArray("hand",hidden);
            }list.add(row);
        }t.put("players",list);
        return new ZJHStatePayload(key,id,version,sequences.getOrDefault(p.getUUID(),-1L),t);
    }
    private static Item resolve(String id) {
        ResourceLocation r=ResourceLocation.tryParse(id);return r==null?Items.AIR:BuiltInRegistries.ITEM.get(r);
    }
    private Item chip(){return resolve(table.chip);}
}
