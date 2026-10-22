package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.blockentity.ZJHTableBlockEntity;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.game.server.zjh.*;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static com.jokernan.craftycards.network.payload.ZJHActionPayload.Action;

@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class ZJHGameTest {
    @GameTest(template="ddz_table_place",timeoutTicks=100) public static void timeoutSettlementAlsoAutomaticallyReleasesTheTable(GameTestHelper h) {
        var f=new Fixture(h,2,true);
        var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(f.key.pos());be.timeout=20;
        f.start();
        h.runAfterDelay(26,()->{try {
            h.assertTrue(f.s().finished()&&f.manager.session(f.key)==null,"超时收尾也必须自动移除会话");
            int total=0;for(var p:f.players){h.assertTrue(!f.manager.contains(p),"不能要求超时结算后手动退局");total+=ZJHChipEscrow.count(p,Items.DIAMOND);}
            h.assertTrue(total==80,"自动退局后仍精确结算且价值守恒");h.succeed();
        }finally{f.close();}});
    }
    @GameTest(template="ddz_table_place") public static void configurationMustBeSavedBeforeJoining(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(f.key.pos());be.configured=false;
            f.act(0,Action.JOIN,0,-1);
            h.assertTrue(!f.s().contains(f.players[0]),"尚未保存配置不能入局，包括无物品筹码模式");
            f.act(0,Action.CONFIG,0,-1);f.act(1,Action.JOIN,0,-1);
            h.assertTrue(!f.s().contains(f.players[1]),"编辑锁期间不能入局");
            var view=f.s().snapshotFor(f.players[0],"",false);
            f.manager.handle(f.players[0],new ZJHActionPayload(f.key,view.session(),view.version(),f.sequence++,Action.SAVE,0,-1,be.config()));
            h.assertTrue(be.configured,"配置保存后标记可用");
            f.start();h.assertTrue(f.s().engine()!=null&&f.s().engine().size()==3,"保存后满人自动开局");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place",timeoutTicks=100) public static void restoringBudgetAutomaticallyStartsWaitingPlayers(GameTestHelper h) {
        var f=new Fixture(h,3,true);
        try {
            f.act(0,Action.JOIN,0,-1);f.act(1,Action.JOIN,0,-1);
            f.players[0].getInventory().clearContent();f.act(2,Action.JOIN,0,-1);
            h.assertTrue(f.s().engine()==null,"等待者拿走筹码后不能部分扣款开局");
            h.assertTrue(f.s().snapshotFor(f.players[1],"",false).data().getString("waitingReason").contains("筹码不足"),"等待界面应解释未开局的原因");
            f.players[0].getInventory().add(new ItemStack(Items.DIAMOND,40));
            h.runAfterDelay(7,()->{try {
                h.assertTrue(f.s().engine()!=null,"补足预算后自动开局，无需额外操作");h.succeed();
            }finally{f.close();}});
        }catch(RuntimeException|Error error){f.close();throw error;}
    }
    @GameTest(template="ddz_table_place") public static void savingLowerPlayerThresholdStartsWithoutConfigurationPlayerJoining(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.act(0,Action.JOIN,0,-1);f.act(1,Action.JOIN,0,-1);
            f.act(2,Action.CONFIG,0,-1);
            var config=f.s().snapshotFor(f.players[2],"",false);var rules=config.data().getCompound("config").copy();rules.putInt("minimum",2);
            f.manager.handle(f.players[2],new ZJHActionPayload(f.key,config.session(),config.version(),f.sequence++,Action.SAVE,0,-1,rules));
            h.assertTrue(f.s().engine()!=null&&f.s().engine().size()==2,"保存后达到人数直接开局，配置者无须参局");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void blockBudgetsSettleByBaseValue(GameTestHelper h) {
        try(var f=new Fixture(h,3,true)) {
            for(var p:f.players){p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.DIAMOND_BLOCK,5));}
            f.start();var g=f.s().engine();h.assertTrue(g!=null,"整块筹码应通过开局预算审核");
            for(int i=0;i<6;i++)f.call();
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            int total=0;for(int i=0;i<3;i++) {
                int value=ZJHChipEscrow.count(f.players[i],Items.DIAMOND);total+=value;
                h.assertTrue(value==45-g.contribution(i)+g.payout(i),"整块本金及找零结算应精确");
            }h.assertTrue(total==135,"总价值不能随兑换增减");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void repeatedHandsKeepAllThreeSeatsInRotation(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            Set<UUID> sessions=new HashSet<>();
            for(int hand=0;hand<4;hand++) {
                f.start();var g=f.s().engine();
                h.assertTrue(sessions.add(f.s().snapshotFor(f.players[0],"",false).session()),"新局必须有独立会话身份");
                h.assertTrue(g!=null&&g.size()==3,"重开后必须保留三人");
                for(int round=0;round<3;round++) {
                    Set<UUID> acted=new HashSet<>();
                    for(int step=0;step<3;step++) {
                        int seat=g.current();var p=f.players[seat];
                        var state=f.s().snapshotFor(p,"",false);
                        h.assertTrue(state.me()==seat&&state.data().getInt("current")==seat,"本人座位必须与服务端行动座位一致");
                        h.assertTrue(state.players().getCompound(seat).getBoolean("active"),"新一局所有玩家都能行动");
                        h.assertTrue(acted.add(p.getUUID()),"一整轮不能漏人");f.call();
                    }
                    h.assertTrue(acted.size()==3,"三人都应轮到");
                }
                while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
                h.assertTrue(f.manager.session(f.key)==null,"结算自动移除旧会话，无需逐个退出");
            }h.succeed();
        }
    }
    private static final BlockPos CORE=new BlockPos(3,1,3);
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final TableKey key;final ServerPlayer[] players;
        final ZJHTableManager manager=ZJHTableManager.getInstance();
        ZJHSession lastSession;
        long sequence=10;
        Fixture(GameTestHelper h,int count,boolean chips) {
            this.h=h;h.setBlock(CORE,InitItems.ZJH_TABLE.get());key=new TableKey(h.getLevel().dimension(),h.absolutePos(CORE));
            var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(key.pos());
            be.minimum=count;be.chips=chips;be.chip="minecraft:diamond";be.ante=1;be.cap=40;be.timeout=600;be.configure(be.config());
            players=new ServerPlayer[count];
            for(int i=0;i<count;i++) {
                players[i]=DDZTestSupport.atTable(h,CORE,h.makeMockServerPlayerInLevel());
                players[i].getInventory().add(new ItemStack(Items.DIAMOND,40));

            }
        }
        // Retain a test-only reference to inspect immutable results after the manager releases a completed hand.
        ZJHSession s(){var active=manager.session(key);if(active!=null)lastSession=active;return lastSession;}
        void act(int p,Action action,int value,int target) {
            var snap=s()==null?null:s().snapshotFor(players[p],"",false);
            manager.handle(players[p],new ZJHActionPayload(key,snap==null?ZJHActionPayload.NONE:snap.session(),
                snap==null?-1:snap.version(),sequence++,action,value,target,new CompoundTag()));
            s();
        }
        void start(){for(int i=0;i<players.length;i++)if(s()==null||!s().contains(players[i]))act(i,Action.JOIN,0,-1);}
        void call(){act(s().engine().current(),Action.CALL,0,-1);}
        @Override public void close(){manager.removed(h.getLevel(),key.pos());for(ServerPlayer p:players)h.getLevel().getServer().getPlayerList().remove(p);}
    }
    @GameTest(template="ddz_table_place") public static void revealPrivacyAndAllHandsAtSettlement(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.start();var g=f.s().engine();h.assertTrue(g!=null,"应开局");int p=g.current(),other=(p+1)%3;
            var before=f.s().snapshotFor(f.players[p],"",false);
            h.assertTrue(Arrays.stream(before.players().getCompound(p).getIntArray("hand")).allMatch(c->c==-1),"本人暗牌不得下发");
            f.act(other,Action.REVEAL,7,-1);h.assertTrue(g.revealedCount(other)==0,"非本人回合不能翻牌");
            f.act(p,Action.REVEAL,2,-1);
            var own=f.s().snapshotFor(f.players[p],"",false);int[] hand=own.players().getCompound(p).getIntArray("hand");
            h.assertTrue(hand[0]==-1&&hand[1]>=0&&hand[2]==-1,"只下发已翻位置");
            var opponent=f.s().snapshotFor(f.players[other],"",false).players().getCompound(p);
            h.assertTrue(opponent.getInt("revealed")==1,"其他人知道已翻张数");
            h.assertTrue(Arrays.stream(opponent.getIntArray("hand")).allMatch(c->c==-1),"对手不能获取已翻牌面");
            f.act(p,Action.FOLD,0,-1);f.act(g.current(),Action.FOLD,0,-1);
            var result=f.s().snapshotFor(f.players[other],"",false);
            for(Tag t:result.players())h.assertTrue(Arrays.stream(((CompoundTag)t).getIntArray("hand")).allMatch(c->c>=0),"结算必须包含弃牌者全部手牌");
            ServerPlayer outsider=DDZTestSupport.atTable(h,CORE,h.makeMockServerPlayerInLevel());
            var outsiderView=f.s().snapshotFor(outsider,"",false);
            for(Tag t:outsiderView.players())h.assertTrue(Arrays.stream(((CompoundTag)t).getIntArray("hand")).allMatch(c->c==-1),"未参局未观战者不能借错误响应获取结算手牌");
            h.getLevel().getServer().getPlayerList().remove(outsider);
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void escrowSettlementConservesOriginalItems(GameTestHelper h) {
        try(var f=new Fixture(h,3,true)) {
            for(ServerPlayer p:f.players)for(int i=0;i<p.getInventory().getContainerSize();i++) {
                ItemStack stack=p.getInventory().getItem(i);if(stack.is(Items.DIAMOND))stack.set(DataComponents.CUSTOM_NAME,Component.literal("原物筹码"));
            }
            f.start();var g=f.s().engine();h.assertTrue(g!=null,"应开局");
            for(int i=0;i<7;i++)f.call();
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            int sum=0;
            for(int p=0;p<3;p++) {
                int count=ZJHChipEscrow.count(f.players[p],Items.DIAMOND);sum+=count;
                h.assertTrue(count==40-g.contribution(p)+g.payout(p),"每人净变化应与账本一致");
                for(int i=0;i<f.players[p].getInventory().getContainerSize();i++) {
                    ItemStack stack=f.players[p].getInventory().getItem(i);
                    if(stack.is(Items.DIAMOND))h.assertTrue(stack.getHoverName().getString().equals("原物筹码"),"组件必须保留");
                }
            }
            h.assertTrue(sum==120,"结算物品总数守恒");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void duplicateAndStaleActionsDoNotChargeTwice(GameTestHelper h) {
        try(var f=new Fixture(h,2,true)) {
            f.start();int p=f.s().engine().current();var snap=f.s().snapshotFor(f.players[p],"",false);
            var packet=new ZJHActionPayload(f.key,snap.session(),snap.version(),100,Action.CALL,0,-1,new CompoundTag());
            f.manager.handle(f.players[p],packet);long pot=f.s().engine().pot();
            f.manager.handle(f.players[p],packet);
            f.manager.handle(f.players[p],new ZJHActionPayload(f.key,snap.session(),snap.version(),101,Action.CALL,0,-1,new CompoundTag()));
            h.assertTrue(f.s().engine().pot()==pot,"重复和陈旧操作不得多扣款");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void compareAfterTwoRoundsChargesOnlyInitiator(GameTestHelper h) {
        try(var f=new Fixture(h,3,true)) {
            f.start();var g=f.s().engine();int first=g.current(),target=(first+1)%3;
            f.act(first,Action.COMPARE,0,target);h.assertTrue(g.pot()==3,"前两轮不得比牌");
            for(int i=0;i<6;i++)f.call();
            int who=g.current();target=(who+1)%3;long before=g.contribution(target),pot=g.pot();
            f.act(who,Action.COMPARE,0,target);
            h.assertTrue(g.contribution(target)==before&&g.pot()==pot+2,"比牌只收发起者双倍跟注费");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void removeTableRefundsAndConfigPersists(GameTestHelper h) {
        try(var f=new Fixture(h,3,true)) {
            f.start();for(int i=0;i<5;i++)f.call();
            f.manager.removed(h.getLevel(),f.key.pos());
            for(ServerPlayer p:f.players)h.assertTrue(ZJHChipEscrow.count(p,Items.DIAMOND)==40,"拆桌全额退款");
            var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(f.key.pos());
            var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored=new ZJHTableBlockEntity(f.key.pos(),InitItems.ZJH_TABLE.get().defaultBlockState());
            restored.loadWithComponents(tag,h.getLevel().registryAccess());
            h.assertTrue(restored.cap==40&&restored.chip.equals("minecraft:diamond")&&restored.minimum==3&&restored.configured,"配置持久化往返");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void sixPlayersCanCompleteAndPrepareAgain(GameTestHelper h) {
        try(var f=new Fixture(h,6,false)) {
            f.start();var g=f.s().engine();
            h.assertTrue(g.size()==6,"支持六人");
            for(int i=0;i<12;i++)f.call();h.assertTrue(g.round()==3,"六人两整轮后为第三轮");
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            h.assertTrue(f.s().snapshotFor(f.players[0],"",false).players().size()==6,"结果包含六人");
            f.start();h.assertTrue(f.s().engine()!=g&&!f.s().engine().settled(),"自动退局后重新加入即自动开下一局");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void newPlayersCanJoinWhilePreviousResultsRemainFrozen(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.start();var g=f.s().engine();
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            var previous=f.s();var before=previous.snapshotFor(f.players[1],"",false);
            h.assertTrue(f.manager.session(f.key)==null,"旧局已自动从管理器移除");
            for(var p:f.players)h.assertTrue(!f.manager.contains(p),"结算后所有人自动退局");
            f.act(0,Action.JOIN,0,-1);
            var after=previous.snapshotFor(f.players[1],"",false);
            h.assertTrue(after.data().equals(before.data()),"新局加入不能改动旧局结果");
            h.assertTrue(before.data().getBoolean("autoExited"),"结算快照说明已自动退局");
            var joined=f.s().snapshotFor(f.players[0],"",false);
            h.assertTrue(joined.phase().equals("WAITING")&&!joined.session().equals(before.session()),"牌桌立即允许新会话加入，不等旧客户端关结果");
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void revealCanBeRepeatedUntilAllThreeCardsAreShown(GameTestHelper h) {
        try(var f=new Fixture(h,2,false)) {
            f.start();var g=f.s().engine();int who=g.current();
            // 用户实机反馈"明牌只能选一次"：服务端本来就允许逐张翻，这里把契约钉住
            f.act(who,Action.REVEAL,1,-1);h.assertTrue(g.revealedCount(who)==1,"翻开第 1 张");
            f.act(who,Action.REVEAL,2,-1);h.assertTrue(g.revealedCount(who)==2,"同一回合可以继续翻第 2 张");
            f.act(who,Action.REVEAL,4,-1);h.assertTrue(g.revealedCount(who)==3,"第 3 张也能翻");
            h.assertTrue(g.looked(who),"翻满三张仍是明注");
            h.assertTrue(g.current()==who,"翻牌不结束回合");
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void autoStartDoesNotRequireTheFirstPlayerOrAHost(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(f.key.pos());be.minimum=2;
            f.act(1,Action.JOIN,0,-1);h.assertTrue(f.s().engine()==null,"未满人数不能开局");
            f.act(2,Action.JOIN,0,-1);
            h.assertTrue(f.s().engine()!=null&&f.s().engine().size()==2,"任意两人加入即开局，无需第一个玩家参局或批准");
            h.assertTrue(!f.s().snapshotFor(f.players[1],"",false).data().contains("host"),"快照不再分配房主");
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void settlementAutomaticallyReleasesAllSeatsAndAllowsAnotherHand(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.start();var g=f.s().engine();
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            var after=f.s().snapshotFor(f.players[0],"",false);
            h.assertTrue(after.phase().equals("SETTLED")&&after.players().size()==3,"退局后结果仍包含所有原始手牌");
            h.assertTrue(f.manager.session(f.key)==null,"结算不能占用牌桌会话");
            for(var p:f.players)h.assertTrue(!f.manager.contains(p),"玩家无需额外退出操作");
            f.start();
            h.assertTrue(f.s().engine()!=null&&f.s().engine().size()==3,"清空的桌子重新加入即可开局");
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void legacyReadyAndStartCannotBypassAutomaticLifecycle(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.act(0,Action.JOIN,0,-1);f.act(0,Action.READY,0,-1);f.act(0,Action.START,0,-1);
            h.assertTrue(f.s().engine()==null,"旧准备和手动开局请求不能绕过人数条件");
            f.act(1,Action.JOIN,0,-1);h.assertTrue(f.s().engine()==null,"第二人加入后仍等第三人");
            f.act(2,Action.JOIN,0,-1);var g=f.s().engine();
            h.assertTrue(g!=null&&g.size()==3,"第三人加入立即开局，无需准备");
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            f.act(0,Action.LOBBY,0,-1);
            h.assertTrue(f.s().engine()==g&&g.settled(),"旧房主重开请求不能清除全桌结算");
            f.start();h.assertTrue(f.s().engine()!=g&&!f.s().engine().settled(),"全员重新加入自动开新局");
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void idleTableIsConfigurableByAnyoneWithATemporaryLock(GameTestHelper h) {
        try(var f=new Fixture(h,2,false)) {
            ServerPlayer first=DDZTestSupport.atTable(h,CORE,h.makeMockServerPlayerInLevel());
            ServerPlayer second=DDZTestSupport.atTable(h,CORE,h.makeMockServerPlayerInLevel());
            f.manager.handle(first,new ZJHActionPayload(f.key,ZJHActionPayload.NONE,-1,500,Action.CONFIG,0,-1,new CompoundTag()));
            var view=f.s().snapshotFor(first,"",false);
            h.assertTrue(view.data().getBoolean("configOpen"),"空桌任何附近玩家都能配置，不再只限房主");
            h.assertTrue(view.me()==-1,"配置者不会因此入座或被静默转成观战");
            f.manager.handle(second,new ZJHActionPayload(f.key,ZJHActionPayload.NONE,-1,501,Action.CONFIG,0,-1,new CompoundTag()));
            h.assertTrue(!f.s().snapshotFor(second,"",false).data().getBoolean("configOpen"),"同一时刻只有一人能编辑");
            h.getLevel().getServer().getPlayerList().remove(first);
            h.getLevel().getServer().getPlayerList().remove(second);
            h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void escrowSaveRecoveryAndDeliveryAreIdempotent(GameTestHelper h) {
        try(var f=new Fixture(h,2,false)) {
            var original=new ZJHChipEscrow();UUID id=UUID.randomUUID();
            for(ServerPlayer p:f.players) {
                for(int i=0;i<p.getInventory().getContainerSize();i++) {
                    ItemStack stack=p.getInventory().getItem(i);
                    if(stack.is(Items.DIAMOND))stack.set(DataComponents.CUSTOM_NAME,Component.literal("恢复原物"));
                }
                original.deposit(id,p,Items.DIAMOND,40);original.charge(id,p.getUUID(),5);
            }
            var restored=ZJHChipEscrow.load(original.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.held(id)==80,"持久化保留全部托管物品");
            restored.recover();restored.recover();
            for(ServerPlayer p:f.players) {
                restored.deliver(p);restored.deliver(p);
                h.assertTrue(ZJHChipEscrow.count(p,Items.DIAMOND)==40,"重复恢复及领取不得重复发放");
                for(int i=0;i<p.getInventory().getContainerSize();i++) {
                    ItemStack stack=p.getInventory().getItem(i);
                    if(stack.is(Items.DIAMOND))h.assertTrue(stack.getHoverName().getString().equals("恢复原物"),"重启恢复保留组件");
                }
            }h.succeed();
        }
    }
    @GameTest(template="ddz_table_place",timeoutTicks=100) public static void revealDoesNotExtendTimeout(GameTestHelper h) {
        var f=new Fixture(h,3,true);
        var be=(ZJHTableBlockEntity)h.getLevel().getBlockEntity(f.key.pos());be.timeout=20;
        f.start();int first=f.s().engine().current();
        h.runAfterDelay(10,()->f.act(first,Action.REVEAL,1,-1));
        h.runAfterDelay(26,()->{
            try{h.assertTrue(!f.s().engine().active(first),"翻牌不得延长行动时限");h.succeed();}
            finally{f.close();}
        });
    }
    @GameTest(template="ddz_table_place") public static void leavingKeepsContributionAndRefundsUnusedBudget(GameTestHelper h) {
        try(var f=new Fixture(h,3,true)) {
            f.start();var g=f.s().engine();int who=g.current();f.act(who,Action.CALL,0,-1);
            f.act(who,Action.LEAVE,0,-1);
            h.assertTrue(ZJHChipEscrow.count(f.players[who],Items.DIAMOND)==38,"退回未下注预算，已投入2保留锅底");
            h.assertTrue(!g.active(who),"离开者出局");
            while(!g.settled())f.act(g.current(),Action.FOLD,0,-1);
            int total=0;for(ServerPlayer p:f.players)total+=ZJHChipEscrow.count(p,Items.DIAMOND);
            h.assertTrue(total==120,"离局后结算仍守恒");
            h.assertTrue(f.s().snapshotFor(f.players[(who+1)%3],"",false).players().size()==3,"离开者保留在结算名单");h.succeed();
        }
    }
    @GameTest(template="ddz_table_place") public static void snapshotCodecRoundTripsOnlyAuthorizedFaces(GameTestHelper h) {
        try(var f=new Fixture(h,3,false)) {
            f.start();int who=f.s().engine().current();f.act(who,Action.REVEAL,5,-1);
            var snapshot=f.s().snapshotFor(f.players[who],"序列化检查",false);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                ZJHStatePayload.STREAM_CODEC.encode(buffer,snapshot);var result=ZJHStatePayload.STREAM_CODEC.decode(buffer);
                h.assertTrue(snapshot.data().equals(result.data())&&snapshot.table().equals(result.table()),"快照往返一致");
                for(int i=0;i<3;i++)if(i!=who)h.assertTrue(Arrays.stream(result.players().getCompound(i).getIntArray("hand")).allMatch(c->c==-1),"序列化不得泄露其他玩家牌面");
            }finally{buffer.release();}
            h.succeed();
        }
    }
}

