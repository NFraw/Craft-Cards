package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.game.server.NeoForgeTableEvents;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 筹码规则的交付测试：**房主先给本桌定筹码 → 审核背包数量 → 够数才能入局**。
 *
 * <p>这一桌用不用筹码、用哪种、每人押几个，都是<b>桌子自己的配置</b>（存方块实体，房主在
 * Shift+右键打开的配置界面里改）；{@code server.json} 只管"全世界的桌子能不能用筹码"这个总闸门。</p>
 *
 * <p>这条链上任何一环缺失，玩家侧的表现都是"右键牌桌没反应"——不报错、也没提示，
 * 极难自查。故每一环都单独固定：本桌没配筹码时拒绝、数量不足拒绝、白名单外拒绝、
 * 以及达标后确实收 1 个进物品池并进入牌局。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZChipBettingGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /** 本桌还没配筹码时不能入局——房主必须先定下来，否则无从审核各家的物品。 */
    @GameTest(template = TEMPLATE)
    public static void joinRefusedUntilChipDeclared(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.stockChips(player);
        try {
            manager.join(player, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话应已建立（配置是开局前第一步，牌桌需要先存在）");
                return;
            }
            if (session.playerCount() != 0 || session.contains(player)) {
                helper.fail("本桌还没配筹码时不该允许入局");
                return;
            }
            // 房主配好筹码之后即可入局
            DDZTestSupport.declareChip(manager, player, key, DDZTestSupport.CHIP);
            manager.join(player, key);
            if (!session.contains(player)) {
                helper.fail("配好筹码后应能入局");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, player);
        }
    }

    /** 背包数量不足时拒绝入局，且不占座位；补足之后可以入局。 */
    @GameTest(template = TEMPLATE)
    public static void insufficientChipsRefused(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer rich = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer poor = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            DDZTestSupport.stockChips(rich);
            DDZTestSupport.declareChip(manager, rich, key, DDZTestSupport.CHIP);
            // 只给 1 个（门槛是 chipRequiredCount，默认 2）
            poor.getInventory().add(new ItemStack(DDZTestSupport.CHIP, 1));

            manager.join(poor, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            if (session.contains(poor)) {
                helper.fail("筹码只有 1 个（门槛 " + ServerGameConfig.requiredChips() + "）时不该允许入局");
                return;
            }

            // 补到门槛数量后即可入局，并被收走 1 个进物品池
            poor.getInventory().add(new ItemStack(DDZTestSupport.CHIP, ServerGameConfig.requiredChips() - 1));
            manager.join(poor, key);
            if (!session.contains(poor)) {
                helper.fail("补足筹码后应能入局");
                return;
            }
            int remaining = DDZTestSupport.countOf(poor, DDZTestSupport.CHIP);
            if (remaining != ServerGameConfig.requiredChips() - 1) {
                helper.fail("入局应把 1 个筹码收进物品池，剩余应为 "
                    + (ServerGameConfig.requiredChips() - 1) + "，实际 " + remaining);
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, rich, poor);
        }
    }

    /**
     * 入局门槛按<b>本桌</b>的值审核（而不是底注 × 6），且快照把它下发给客户端。
     *
     * <p>本桌的值来自"第一次被用到时从全局默认值种入"：这里全局默认门槛是 12（底注 1 的派生值是 6），
     * 只备 11 个必须被拒——如果实现退回派生值就会误放行，这个用例正好把两者区分开。
     * 门槛是"背包装够才准入"的审核，不影响入局实收的押注数（仍是底注个）。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void customEntryCountIsEnforced(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        ServerGameConfig.chipEntryCount = 12;
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer rich = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer poor = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            DDZTestSupport.stockChips(rich);
            DDZTestSupport.declareChip(manager, rich, key, DDZTestSupport.CHIP);
            poor.getInventory().add(new ItemStack(DDZTestSupport.CHIP, 11));

            manager.join(poor, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            if (session.contains(poor)) {
                helper.fail("手动门槛 12 时不该放行只有 11 个的玩家（派生门槛 6 会误放行）");
                return;
            }

            poor.getInventory().add(new ItemStack(DDZTestSupport.CHIP, 1));   // 刚好 12
            manager.join(poor, key);
            if (!session.contains(poor)) {
                helper.fail("补到手动门槛 12 后应能入局");
                return;
            }
            if (session.snapshotFor(poor).chipRequired() != 12) {
                helper.fail("快照应下发手动门槛 12，实际 " + session.snapshotFor(poor).chipRequired());
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.chipEntryCount = 0;   // 静态状态要在用例之间还原
            DDZTestSupport.cleanup(manager, key, helper, rich, poor);
        }
    }

    /** 白名单之外的物品不能作为本桌筹码。 */
    @GameTest(template = TEMPLATE)
    public static void nonChipItemRejected(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            // 拿木棍声明 → 应被拒，筹码仍为未声明
            DDZTestSupport.declareChip(manager, player, key, Items.STICK);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            GameStatePayload snap = session.snapshotFor(player);
            if (!snap.betItem().isEmpty()) {
                helper.fail("木棍不该被接受为筹码，实际筹码为 " + snap.betItem());
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, player);
        }
    }

    /**
     * 房主配好筹码后，快照要带上筹码物品与所需数量——桌面悬浮图标与 HUD 文案全靠它们，
     * 而"桌面显示本桌筹码是什么"这件事没有别的数据来源。
     */
    @GameTest(template = TEMPLATE)
    public static void declaredChipReachesSnapshot(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            DDZTestSupport.declareChip(manager, player, key, Items.GOLD_INGOT);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            GameStatePayload snap = session.snapshotFor(player);
            if (!"minecraft:gold_ingot".equals(snap.betItem())) {
                helper.fail("快照里的筹码应为 minecraft:gold_ingot，实际 " + snap.betItem());
                return;
            }
            if (snap.chipRequired() != ServerGameConfig.requiredChips()) {
                helper.fail("快照应带上所需数量 " + ServerGameConfig.requiredChips()
                    + "，实际 " + snap.chipRequired());
                return;
            }
            // 开局前（还没有人入座）筹码还能改。注意玩家必须持有被声明的那一种物品（金锭），
            // 不是测试默认的钻石——筹码类型是本桌配置，不按背包里的东西推断
            DDZTestSupport.stockChips(player, Items.GOLD_INGOT, ServerGameConfig.requiredChips());
            manager.join(player, key);
            DDZTestSupport.declareChip(manager, player, key, DDZTestSupport.CHIP);
            if (!"minecraft:gold_ingot".equals(session.snapshotFor(player).betItem())) {
                helper.fail("已有人入局后本桌筹码不该还能被改掉");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, player);
        }
    }

    /**
     * 潜行 + 手持物品右键牌桌时，必须把交互强制交给方块。
     *
     * <p>这是修一个真实缺陷的回归测试：Minecraft 在"潜行 + 手持物品"时会跳过方块交互
     * （把右键让给物品），于是"潜行+手持筹码声明"与"手持扑克时 Shift+右键离开"都收不到右键。
     * 用例直接触发 {@code RightClickBlock} 监听器并断言 {@code useBlock} 被置为 TRUE——
     * 单测只覆盖判定函数，这里覆盖的是**接线**（事件是否真的被改写、是否误伤其他方块）。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void sneakingWithItemForcesTableInteraction(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();
        BlockPos stone = CORE.offset(2, 0, 0);
        helper.setBlock(stone, net.minecraft.world.level.block.Blocks.STONE);

        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            player.setShiftKeyDown(true);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));

            var tableEvent = rightClickEvent(helper, player, CORE);
            NeoForgeTableEvents.onRightClickBlock(tableEvent);
            if (tableEvent.getUseBlock() != TriState.TRUE) {
                helper.fail("潜行+手持物品右键牌桌时，应强制把交互交给方块（否则声明筹码/离开都收不到右键），"
                    + "实际 useBlock=" + tableEvent.getUseBlock());
                return;
            }

            // 不能误伤其他方块：否则原版"潜行对着方块放东西"就被破坏了
            var stoneEvent = rightClickEvent(helper, player, stone);
            NeoForgeTableEvents.onRightClickBlock(stoneEvent);
            if (stoneEvent.getUseBlock() != TriState.DEFAULT) {
                helper.fail("非牌桌方块不该被改写，实际 useBlock=" + stoneEvent.getUseBlock());
                return;
            }

            // 不潜行时不干预
            player.setShiftKeyDown(false);
            var normalEvent = rightClickEvent(helper, player, CORE);
            NeoForgeTableEvents.onRightClickBlock(normalEvent);
            if (normalEvent.getUseBlock() != TriState.DEFAULT) {
                helper.fail("不潜行时不该干预，实际 useBlock=" + normalEvent.getUseBlock());
                return;
            }
            helper.succeed();
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, player);
        }
    }

    /**
     * {@code server.json} 关掉筹码（{@code chipsRequired = false}）时的"纯娱乐局"验收：
     * 不必声明、不必持有筹码，三人右键牌桌即可开局；快照下发 0 让客户端切成"不需要筹码"；
     * 声明筹码被明确拒绝（不能偷偷把桌子变成赌局）；整局打完不转移任何物品。
     *
     * <p>没有这条，关掉筹码后玩家只会遇到"右键没反应"——而这条链（入局 → 开局 → 结算）
     * 横跨 {@code DDZSession} 与配置读取，只有 GameTest 能整条跑通。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void chipsDisabledPlaysWithoutChips(GameTestHelper helper) {
        boolean savedChipsRequired = ServerGameConfig.chipsRequired;
        ServerGameConfig.chipsRequired = false;   // 本用例的前提与 forceBettingDefaults 相反

        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        try {
            // 背包里放点筹码物品（当作普通物品），用来验证"结算不碰它们"
            for (ServerPlayer p : players) DDZTestSupport.stockChips(p, Items.DIAMOND, 3);

            // 就算房主在配置界面里给本桌选了筹码，全局闸门关着时这一桌照样不玩筹码（闸门与桌开关是"与"）
            DDZTestSupport.declareChip(manager, p1, key, DDZTestSupport.CHIP);
            DDZSession configured = manager.session(key);
            if (configured != null) {
                GameStatePayload configuredSnap = configured.snapshotFor(p1);
                if (configuredSnap.chipRequired() != 0) {
                    helper.fail("全局闸门关掉时，桌上配了筹码也不该开始收押注（chipRequired 应为 0），实际 "
                        + configuredSnap.chipRequired());
                    return;
                }
                // 但"桌上配的是哪个物品"照发：配置界面要拿它回填，玩家关掉再打开时不用重新选一遍
                if (!"minecraft:diamond".equals(configuredSnap.betItem())) {
                    helper.fail("桌上配的筹码类型应照发给客户端（配置界面回填用），实际 \""
                        + configuredSnap.betItem() + "\"");
                    return;
                }
            }

            // 不校验数量：直接入局就该开局
            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("不需要筹码时三人右键牌桌就该开局，实际阶段 "
                    + (session == null ? "无会话" : session.phase()));
                return;
            }
            GameStatePayload snap = session.snapshotFor(p1);
            if (snap.chipRequired() != 0) {
                helper.fail("不需要筹码时应下发 chipRequired=0，实际 " + snap.chipRequired());
                return;
            }
            int atStart = chipsOf(players);
            if (atStart != 9) {
                helper.fail("入局不该收走任何物品（三人各 3 个应仍为 9 个），实际 " + atStart);
                return;
            }

            // 叫满 3 分定地主（不需要筹码的桌子照样要叫分）
            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }

            // 打完这一局（只出单张：每步都合法，且每轮至少消一张牌）
            if (!DDZTestSupport.playOutWithSingleCards(helper, session, players)) {
                return;
            }

            if (chipsOf(players) != atStart) {
                helper.fail("不需要筹码的牌局结算不该转移物品：开局 " + atStart
                    + " 个，结算后 " + chipsOf(players) + " 个");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.chipsRequired = savedChipsRequired;
            DDZTestSupport.cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /** 三位玩家背包里的筹码物品总数。 */
    private static int chipsOf(ServerPlayer[] players) {
        int total = 0;
        for (ServerPlayer p : players) total += DDZTestSupport.countOf(p, DDZTestSupport.CHIP);
        return total;
    }

    /** 构造一个对该方块的右键事件。 */    private static PlayerInteractEvent.RightClickBlock rightClickEvent(
            GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        var hit = new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        return new PlayerInteractEvent.RightClickBlock(
            player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
    }

    /** 被拒绝的入局不应改变牌局阶段。 */
    @GameTest(template = TEMPLATE)
    public static void refusedJoinLeavesPhaseWaiting(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            DDZTestSupport.declareChip(manager, player, key, DDZTestSupport.CHIP);
            manager.join(player, key); // 无筹码 → 被拒
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.WAITING) {
                helper.fail("被拒绝的入局不该推进牌局阶段");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, player);
        }
    }
}
