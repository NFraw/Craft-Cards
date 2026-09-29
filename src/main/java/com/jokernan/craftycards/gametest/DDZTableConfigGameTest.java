package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * <b>每桌筹码配置</b>的交付测试：房主是谁、配置界面互斥、会话真的按本桌的值审核与收押注、
 * 本桌关掉筹码就是纯娱乐局，以及判负的金额守恒。
 *
 * <p>为什么必须用 GameTest：这一整块的状态活在<b>方块实体</b>（每桌一份）与 {@code ServerSession}
 * 上，还要 {@code ServerPlayer}（房主是玩家 UUID、配置锁查在线状态），单测里连类都加载不了。
 * 而它出问题的表现又特别隐蔽——"右键没反应""点了保存没变""判负后东西少了"，都不是崩溃。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZTableConfigGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /** 打开本桌配置界面（Shift+右键）。 */
    private static PlayerActionPayload open(TableKey key) {
        return new PlayerActionPayload(key, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of());
    }

    /** 保存本桌配置（底注在 score、门槛在 cards[0]、筹码类型与开关在末尾两个字段）。 */
    private static PlayerActionPayload save(TableKey key, String chipItem, int stake, int entry,
                                            boolean enabled) {
        return new PlayerActionPayload(key, PlayerActionPayload.Action.SAVE_TABLE_CONFIG, stake,
            List.of(entry), chipItem, enabled);
    }

    /** 关闭本桌配置界面（释放配置锁）。 */
    private static PlayerActionPayload close(TableKey key) {
        return new PlayerActionPayload(key, PlayerActionPayload.Action.CLOSE_TABLE_CONFIG, 0, List.of());
    }

    /**
     * 第一个 Shift+右键这张桌的人成为房主；第二个人拿不到配置权，退回原用途（观战开关）。
     *
     * <p>房主必须能自动产生：桌子刚放下时没有任何"房主"可言，若要求先有人授权，这张桌就永远
     * 配不了。这个用例同时钉住"只有一个房主"与"非房主 Shift+右键不能把观战入口弄丢"。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void hostIsFirstShiftClicker(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer first = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer second = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            manager.handle(first, open(key));
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("Shift+右键配置界面应建立本桌会话");
                return;
            }
            GameStatePayload firstSnap = session.snapshotFor(first);
            if (!firstSnap.iAmHost() || !firstSnap.configOpen()) {
                helper.fail("第一个 Shift+右键的人应成为房主并打开配置界面，实际 iAmHost="
                    + firstSnap.iAmHost() + " configOpen=" + firstSnap.configOpen());
                return;
            }
            // 房主落在桌子上（持久化），不只是内存里的一句话
            DDZTableBlockEntity table = DDZTestSupport.tableEntity(key, first);
            if (table == null || !table.isHost(first.getUUID())) {
                helper.fail("房主应记在本桌的方块实体上");
                return;
            }

            manager.handle(second, open(key));
            GameStatePayload secondSnap = session.snapshotFor(second);
            if (secondSnap.iAmHost() || secondSnap.configOpen()) {
                helper.fail("第二个人不该拿到本桌配置权，实际 iAmHost=" + secondSnap.iAmHost()
                    + " configOpen=" + secondSnap.configOpen());
                return;
            }
            if (!table.isHost(first.getUUID())) {
                helper.fail("第二个人不该把房主顶掉");
                return;
            }
            // 观战入口不能因为多了配置界面就消失：非房主的 Shift+右键要退回观战开关
            if (!session.isWatching(second)) {
                helper.fail("非房主 Shift+右键应退回观战开关");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, first, second);
        }
    }

    /**
     * 同一时间只允许一个人配置本桌：非房主既开不了界面、也改不动本桌的值；
     * 房主关掉界面（或直接发 CLOSE）后锁才释放。
     *
     * <p>防住的是一类真实事故：两个人同时改一桌，后保存的把人家的设置悄悄顶掉。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void configEditorIsExclusive(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer host = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer other = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            DDZTestSupport.declareChip(manager, host, key, DDZTestSupport.CHIP);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            manager.handle(host, open(key));
            if (!session.snapshotFor(host).configOpen()) {
                helper.fail("房主应拿到配置锁");
                return;
            }

            // 第二个人：开不了界面（不是房主），也改不动本桌
            manager.handle(other, open(key));
            if (session.snapshotFor(other).configOpen()) {
                helper.fail("非房主不该拿到配置锁（同一时间只允许一个人配置本桌）");
                return;
            }
            manager.handle(other, save(key, "minecraft:diamond", 1, 999, true));
            if (session.snapshotFor(host).tableEntryCount() == 999) {
                helper.fail("非房主不该能改本桌配置（服务端应按房主 + 锁双重校验）");
                return;
            }
            if (!session.snapshotFor(host).configOpen()) {
                helper.fail("别人尝试配置不该把房主手里的锁抢走");
                return;
            }

            // 房主关掉界面 → 锁释放（下次谁都能重新打开；新的一轮仍由房主来配）
            manager.handle(host, close(key));
            if (session.snapshotFor(host).configOpen()) {
                helper.fail("关闭配置界面后应释放配置锁");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, host, other);
        }
    }

    /**
     * 房主离线后，下一位 Shift+右键的人可以接管这张桌。
     *
     * <p>没有这条，桌子会被"一去不回的房主"锁死：筹码没配的桌子谁都入不了局，
     * 而房主又改不了——等于砖了。（锁本身也会自愈：持有者查不到就按陈旧处理。）</p>
     */
    @GameTest(template = TEMPLATE)
    public static void offlineHostCanBeTakenOver(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer gone = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer next = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            manager.handle(gone, open(key));
            DDZSession session = manager.session(key);
            if (session == null || !session.snapshotFor(gone).iAmHost()) {
                helper.fail("第一个人应成为房主");
                return;
            }
            // 直接把他从玩家列表摘掉（模拟"人没了"）：不比他真的走远/掉线更宽松，
            // 走的是同一条"查不到这个 UUID 了"的判定
            helper.getLevel().getServer().getPlayerList().remove(gone);

            manager.handle(next, open(key));
            GameStatePayload snap = session.snapshotFor(next);
            if (!snap.iAmHost() || !snap.configOpen()) {
                helper.fail("房主离线后，下一位 Shift+右键的人应接管本桌并打开配置界面，实际 iAmHost="
                    + snap.iAmHost() + " configOpen=" + snap.configOpen());
                return;
            }
            DDZTableBlockEntity table = DDZTestSupport.tableEntity(key, next);
            if (table == null || !table.isHost(next.getUUID())) {
                helper.fail("接管后房主应改成新的玩家");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, gone, next);
        }
    }

    /**
     * 会话按<b>本桌</b>配置办事：桌上配成 底注 3 / 门槛 20 后，入局审核按 20、每人实收 3，
     * 快照也把这三项下发给客户端。
     *
     * <p>这是"每桌独立"的核心断言：同一时刻全局配置是 底注 1 / 门槛 6（派生），
     * 如果实现还在读全局，19 个筹码就会被误放行、也只收走 1 个。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void sessionUsesTableConfig(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer host = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer poor = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        try {
            // 先成为房主（顺带把本桌的初始值种进来），再按本桌的值保存
            manager.handle(host, open(key));
            manager.handle(host, save(key, DDZTestSupport.itemId(DDZTestSupport.CHIP), 3, 20, true));

            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            if (session.stake() != 3 || session.requiredChips() != 20) {
                helper.fail("会话应读本桌的值（底注 3 / 门槛 20），实际 底注 " + session.stake()
                    + " / 门槛 " + session.requiredChips());
                return;
            }
            if (ServerGameConfig.chipStake != 1 || ServerGameConfig.requiredChips() != 6) {
                helper.fail("全局配置不该被本桌的保存改掉（应仍是 底注 1 / 门槛 6），实际 "
                    + ServerGameConfig.chipStake + " / " + ServerGameConfig.requiredChips());
                return;
            }

            // 门槛 20：只有 19 个必须被拒（按全局的 6 会误放行）
            DDZTestSupport.stockChips(host, 30);
            DDZTestSupport.stockChips(poor, 19);
            manager.join(host, key);
            manager.join(poor, key);
            if (session.contains(poor)) {
                helper.fail("本桌门槛 20 时，只有 19 个筹码不该允许入局");
                return;
            }

            // 补到 20 个可以入局，且被收走 3 个（本桌底注），不是全局的 1 个
            poor.getInventory().add(new net.minecraft.world.item.ItemStack(DDZTestSupport.CHIP, 1));
            manager.join(poor, key);
            if (!session.contains(poor)) {
                helper.fail("补到本桌门槛 20 后应能入局");
                return;
            }
            int left = DDZTestSupport.countOf(poor, DDZTestSupport.CHIP);
            if (left != 17) {
                helper.fail("入局应按本桌底注收走 3 个（20 - 3 = 17），实际剩 " + left);
                return;
            }

            GameStatePayload snap = session.snapshotFor(poor);
            if (snap.chipRequired() != 20 || snap.betCount() != 3 || snap.tableEntryCount() != 20) {
                helper.fail("快照应下发本桌的 门槛 20 / 底注 3 / 原始门槛 20，实际 "
                    + snap.chipRequired() + " / " + snap.betCount() + " / " + snap.tableEntryCount());
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, host, poor);
        }
    }

    /**
     * 本桌关掉筹码 = 纯娱乐局：不必持有筹码也能入局，一张物品都不转移；
     * 快照下发 {@code chipRequired = 0} 且<b>不带筹码物品</b>（否则客户端会在桌上飘一个筹码图标）。
     *
     * <p>{@code server.json} 的总闸门只是"与"的一边：这里闸门是开的，关的是这一桌。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void tableChipsDisabledPlaysWithoutChips(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        try {
            // 房主把本桌的筹码开关关掉（筹码类型留着，重新打开时不用再选一遍）
            manager.handle(p1, open(key));
            manager.handle(p1, save(key, DDZTestSupport.itemId(DDZTestSupport.CHIP), 1, 6, false));

            DDZSession session = manager.session(key);
            if (session == null || session.requiredChips() != 0) {
                helper.fail("本桌关掉筹码后 requiredChips() 应为 0，实际 "
                    + (session == null ? "无会话" : session.requiredChips()));
                return;
            }
            // 三人背包里放几个筹码物品（当普通物品），用来验证"结算不碰它们"
            for (ServerPlayer p : players) DDZTestSupport.stockChips(p, 3);

            for (ServerPlayer p : players) manager.join(p, key);
            if (session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("不玩筹码时三人入局就该开局，实际阶段 " + session.phase());
                return;
            }
            GameStatePayload snap = session.snapshotFor(p1);
            if (snap.chipRequired() != 0) {
                helper.fail("本桌不玩筹码时应下发 chipRequired=0，实际 " + snap.chipRequired());
                return;
            }
            // 桌上配的筹码类型照发（配置界面回填用）：把"玩不玩"与"配了什么"分开表达，
            // 否则房主关掉筹码再打开界面就会看到"未选择"，顺手一保存就把自己选的类型抹了
            if (!DDZTestSupport.itemId(DDZTestSupport.CHIP).equals(snap.betItem())) {
                helper.fail("本桌配好的筹码类型应照发给客户端，实际 \"" + snap.betItem() + "\"");
                return;
            }
            if (snap.tableChipsEnabled()) {
                helper.fail("快照里的本桌开关应为关（配置界面要拿它回填）");
                return;
            }
            int atStart = chipsOf(players);
            if (atStart != 9) {
                helper.fail("入局不该收走任何物品（三人各 3 个应仍为 9 个），实际 " + atStart);
                return;
            }

            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }
            if (!DDZTestSupport.playOutWithSingleCards(helper, session, players)) return;
            if (chipsOf(players) != atStart) {
                helper.fail("不玩筹码的桌子结算不该转移物品：开局 " + atStart
                    + " 个，结算后 " + chipsOf(players) + " 个");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /**
     * <b>判负的金额守恒</b>（规格里点名还没写的那条）：判负者赔其余两家各「本桌门槛 / 2」，
     * 其余两家自己的押注原样退回，三家筹码总量一个不多一个不少。
     *
     * <p>判负是"物品直接从人身上划走"的路径，最怕两件事：多划了（凭空销毁物品）或漏退了
     * （押注被吞）。这里按<b>净额</b>断言而不是绝对数量：绝对数量会把错误行为固化成期望值。
     * 门槛取 12（偶数）避免奇数取整把差额藏进"少赔 1 个"里。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void forfeitPaysHalfEntryEach(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        int each = 14;   // 门槛 12 + 2 个赔付余量
        try {
            manager.handle(p1, open(key));
            manager.handle(p1, save(key, DDZTestSupport.itemId(DDZTestSupport.CHIP), 1, 12, true));
            for (ServerPlayer p : players) DDZTestSupport.stockChips(p, each);

            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("三人入局后应进入叫分，实际 "
                    + (session == null ? "无会话" : session.phase().toString()));
                return;
            }
            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }

            int per = session.requiredChips() / 2;   // 门槛 12 → 每人 6
            session.forfeit(p1, "测试判负", manager);

            if (manager.session(key) != null) {
                helper.fail("判负后牌局应解散");
                return;
            }
            int[] net = new int[3];
            for (int i = 0; i < 3; i++) net[i] = DDZTestSupport.countOf(players[i], DDZTestSupport.CHIP) - each;
            int total = 0;
            for (ServerPlayer p : players) total += DDZTestSupport.countOf(p, DDZTestSupport.CHIP);
            if (total != each * 3) {
                helper.fail("判负结算后筹码总量应守恒为 " + (each * 3) + "，实际 " + total
                    + "——有物品被静默销毁或凭空产生");
                return;
            }
            if (net[0] != -2 * per) {
                helper.fail("判负者应赔出 " + (2 * per) + " 个（门槛 " + session.requiredChips()
                    + " / 2 各一份），实际净额 " + net[0]);
                return;
            }
            if (net[1] != per || net[2] != per) {
                helper.fail("其余两家应各得 " + per + " 个且自己的押注原样退回，实际 "
                    + net[1] + " / " + net[2]);
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            DDZTestSupport.cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /**
     * 每轮限时到点判负：轮到的玩家在限时内没有任何操作就按判负结算（赔其余两家各 门槛 / 2）。
     *
     * <p>计时靠"轮次指纹"：第一次调用只记指纹，之后超过限时才动手——所以这里必须调两次，
     * 这也正好钉住"计时是从这一轮开始算的，不是从牌局开始算的"。</p>
     *
     * <p>顺带钉住 HUD 倒计时赖以工作的那半边：快照里的 {@code turnRemainingTicks} 要随计时递减
     * （倒计时基准 + 本地续算在客户端，客户端没有单测环境）。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void turnTimeoutForfeits(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        int savedTimeout = ServerGameConfig.turnTimeoutTicks;
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        int each = 14;
        try {
            manager.handle(p1, open(key));
            manager.handle(p1, save(key, DDZTestSupport.itemId(DDZTestSupport.CHIP), 1, 12, true));
            for (ServerPlayer p : players) DDZTestSupport.stockChips(p, each);

            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }
            int slowSeat = session.currentSeat();
            ServerPlayer slow = players[slowSeat];

            ServerGameConfig.turnTimeoutTicks = 20;
            // 时间轴以**真实服务器刻**为起点：tickTurnTimeout 收的是传入的刻（可以往前推），
            // 而快照里的倒计时走的是真实刻 —— 两边同一个基准，用例才能既验递减、又验快照接线
            long t = helper.getLevel().getServer().getTickCount();
            // 递减规则用 turnRemainingTicks(刻) 断言：它接受传入的刻，测试才能用可控的时间轴。
            // （快照里那一个走的是真实服务器刻，用例体在这一刻内跑完，所以它只能验"接线"。）
            if (session.turnRemainingTicks(t) != 20) {
                helper.fail("这一轮刚开始（清扫还没记指纹）时应剩满值 20，实际 "
                    + session.turnRemainingTicks(t));
                return;
            }
            session.tickTurnTimeout(t, manager);          // 第一次：只记这一轮的起点
            if (manager.session(key) == null) {
                helper.fail("刚开始计时就判负了（限时 20 刻）");
                return;
            }
            if (session.turnRemainingTicks(t) != 20) {
                helper.fail("记下起点那一刻剩余应为 20，实际 " + session.turnRemainingTicks(t));
                return;
            }
            if (session.turnRemainingTicks(t + 5) != 15) {
                helper.fail("过了 5 刻应剩 15，实际 " + session.turnRemainingTicks(t + 5));
                return;
            }
            session.tickTurnTimeout(t + 20, manager);     // 恰好到点：还不算超
            if (manager.session(key) == null) {
                helper.fail("刚好等于限时不该判负（应为 超过 限时才判）");
                return;
            }
            // 倒计时走完 = 0（HUD 显示 0s、转红），而不是 -1（那是"不限时"）
            if (session.turnRemainingTicks(t + 20) != 0) {
                helper.fail("到点那一刻应剩 0（不是 -1，-1 表示不限时），实际 "
                    + session.turnRemainingTicks(t + 20));
                return;
            }
            // 快照那条路（HUD 真正读的就是它）也得通：用真实服务器刻算出来的值必须落在 [0, 限时] 内
            int viaSnapshot = session.snapshotFor(slow).turnRemainingTicks();
            if (viaSnapshot < 0 || viaSnapshot > 20) {
                helper.fail("快照里的倒计时应落在 [0, 20]（-1 = 不限时，不该出现），实际 " + viaSnapshot);
                return;
            }
            session.tickTurnTimeout(t + 21, manager);     // 超过限时 → 判负

            if (manager.session(key) != null) {
                helper.fail("超时未出牌应按判负结算并解散牌局");
                return;
            }
            int per = 6;   // 门槛 12 / 2
            int slowNet = DDZTestSupport.countOf(slow, DDZTestSupport.CHIP) - each;
            int total = 0;
            for (ServerPlayer p : players) total += DDZTestSupport.countOf(p, DDZTestSupport.CHIP);
            if (total != each * 3) {
                helper.fail("超时判负后筹码总量应守恒为 " + (each * 3) + "，实际 " + total);
                return;
            }
            if (slowNet != -2 * per) {
                helper.fail("超时的那位应赔出 " + (2 * per) + " 个，实际净额 " + slowNet);
                return;
            }
            for (ServerPlayer p : players) {
                if (p == slow) continue;
                int net = DDZTestSupport.countOf(p, DDZTestSupport.CHIP) - each;
                if (net != per) {
                    helper.fail("其余两家应各得 " + per + " 个，实际 " + net);
                    return;
                }
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.turnTimeoutTicks = savedTimeout;   // 静态状态要在用例之间还原
            DDZTestSupport.cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /**
     * 关掉每轮限时（玩法设置页那个开关写的就是 {@code turnTimeoutTicks = 0}）：
     * 无论过多久都不判负，快照下发 -1（HUD 因此不画倒计时）。
     *
     * <p>这条不钉住的话，"开关"很容易做成只影响界面显示、服务端照旧判负——
     * 玩家看到的会是"我没开限时，怎么还把我判负了"。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void turnTimeoutDisabledNeverForfeits(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        int savedTimeout = ServerGameConfig.turnTimeoutTicks;
        ServerGameConfig.turnTimeoutTicks = 0;   // 本用例的前提：不限时
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        int each = 8;
        try {
            manager.handle(p1, open(key));
            manager.handle(p1, save(key, DDZTestSupport.itemId(DDZTestSupport.CHIP), 1, 6, true));
            for (ServerPlayer p : players) DDZTestSupport.stockChips(p, each);

            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }
            if (session.snapshotFor(p1).turnRemainingTicks() != -1) {
                helper.fail("不限时时快照应下发 -1（HUD 不画倒计时），实际 "
                    + session.snapshotFor(p1).turnRemainingTicks());
                return;
            }

            // 过一小时（72000 刻）也不该判负：限时关闭时这套计时根本不参与
            session.tickTurnTimeout(1000L, manager);
            session.tickTurnTimeout(73000L, manager);
            if (manager.session(key) == null) {
                helper.fail("关掉每轮限时后不该因为超时判负");
                return;
            }
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("牌局应还在出牌阶段，实际 " + session.phase());
                return;
            }
            if (session.snapshotFor(p1).turnRemainingTicks() != -1) {
                helper.fail("不限时时快照始终应下发 -1，实际 "
                    + session.snapshotFor(p1).turnRemainingTicks());
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.turnTimeoutTicks = savedTimeout;   // 静态状态要在用例之间还原
            DDZTestSupport.cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /** 三位玩家背包里的筹码物品总数。 */
    private static int chipsOf(ServerPlayer[] players) {
        int total = 0;
        for (ServerPlayer p : players) total += DDZTestSupport.countOf(p, DDZTestSupport.CHIP);
        return total;
    }
}
