package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import net.minecraft.world.item.Items;

import java.util.List;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 牌局开局流程的游戏内测试：走真实入口 {@link DDZTableManager#join}（与 JOIN 网络包同一条路），
 * 断言"三个人坐满即自动发牌"。
 *
 * <p>存在的理由：这条链路（入局 → 坐满 → {@code engine.shuffleAndDeal()}）横跨
 * {@code DDZTableManager} 与 {@code DDZSession}，都依赖 {@code ServerPlayer}，单测覆盖不到；
 * 曾经在改写 {@code DDZSession} 时漏掉发牌调用，表现为"三人右键桌子却一直停在等待中"，
 * 只能靠人进游戏才发现。此处用模拟玩家把这条链路固定住。
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZSessionGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    /** 牌桌方块在模板内的相对坐标。 */
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /** 三个人依次入局：前两人停在等待，第三人入局后立即发牌进入叫分。 */
    @GameTest(template = TEMPLATE)
    public static void threePlayersStartDealing(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, p2, p3);

        try {
            manager.join(p1, key);
            if (phaseOf(manager, key) != DDZGamePhase.WAITING) {
                helper.fail("只有 1 人时不应开局，实际阶段 " + phaseOf(manager, key));
                return;
            }
            if (manager.session(key).playerCount() != 1) {
                helper.fail("1 人入局后座位数应为 1");
                return;
            }

            manager.join(p2, key);
            if (phaseOf(manager, key) != DDZGamePhase.WAITING) {
                helper.fail("只有 2 人时不应开局，实际阶段 " + phaseOf(manager, key));
                return;
            }

            manager.join(p3, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("三人入局后牌桌会话不应消失");
                return;
            }
            // 坐满 → 发牌 → 进入叫分
            if (session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("三人坐满后应发牌进入叫分阶段，实际阶段 " + session.phase());
                return;
            }
            // 发牌内容：每人 17 张 + 底牌 3 张 = 一副 54 张
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (session.handSize(seat) != DDZEngine.CARDS_PER_PLAYER) {
                    helper.fail("座位 " + seat + " 手牌应为 " + DDZEngine.CARDS_PER_PLAYER
                        + " 张，实际 " + session.handSize(seat));
                    return;
                }
            }
            if (session.dipaiSize() != DDZEngine.DIPI_COUNT) {
                helper.fail("底牌应为 " + DDZEngine.DIPI_COUNT + " 张，实际 " + session.dipaiSize());
                return;
            }
            // 入局应发到斗地主扑克占位物品
            for (ServerPlayer p : new ServerPlayer[]{p1, p2, p3}) {
                if (!hasDdzCard(p)) {
                    helper.fail("入局后应持有斗地主扑克占位物品");
                    return;
                }
            }
            // 解散后应回收（回收发生在 disband，故先断言解散前的持有状态）
            session.disband("测试收尾", manager);
            for (ServerPlayer p : new ServerPlayer[]{p1, p2, p3}) {
                if (hasDdzCard(p)) {
                    helper.fail("牌局解散后应回收斗地主扑克占位物品");
                    return;
                }
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        }
        cleanup(manager, key, helper, p1, p2, p3);
    }

    /**
     * 坐满后右键牌桌 = 切换观战（而不是只回一句"牌桌已满"）。
     * 这条路径服务于"开局前就在服务器里的玩家"：他们不会自动收到牌面数据，
     * 右键一次即可立刻拿到并持续更新，且不受旁观半径限制。
     */
    @GameTest(template = TEMPLATE)
    public static void fullTableTogglesWatching(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        try {
            ServerPlayer a = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
            ServerPlayer b = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
            ServerPlayer c = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
            ServerPlayer watcher = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
            DDZTestSupport.prepareBetting(manager, key, a, b, c);
            manager.join(a, key);
            manager.join(b, key);
            manager.join(c, key);

            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("牌桌会话不应消失");
                return;
            }
            if (session.isWatching(watcher)) {
                helper.fail("未交互前不应处于观战状态");
                return;
            }

            // 坐满后右键：切换为观战，且不占座位、不影响已开始的牌局
            manager.join(watcher, key);
            if (!session.isWatching(watcher)) {
                helper.fail("坐满后右键牌桌应开始观战");
                return;
            }
            if (session.playerCount() != DDZEngine.PLAYER_COUNT) {
                helper.fail("观战者不应占座位，牌桌应保持 " + DDZEngine.PLAYER_COUNT + " 人，实际 " + session.playerCount());
                return;
            }
            if (session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("观战不应影响已开始的牌局，实际阶段 " + session.phase());
                return;
            }

            // 再右键一次：停止观战
            manager.join(watcher, key);
            if (session.isWatching(watcher)) {
                helper.fail("再次右键牌桌应停止观战");
                return;
            }
            if (session.playerCount() != DDZEngine.PLAYER_COUNT) {
                helper.fail("停止观战后座位数不应变化");
                return;
            }
            helper.succeed();
            cleanup(manager, key, helper, a, b, c, watcher);
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        }
    }

    /**
     * 收尾：先从管理器摘桌，再把模拟玩家从玩家列表移除。
     * 顺序不能反——移除玩家会触发 {@code PlayerLoggedOutEvent} → {@code handleLeave}，
     * 若此时会话还在且牌局进行中，会走 {@code disband} 给其余模拟玩家发快照。
     * （单例管理器跨测试共享，不摘桌会污染同批其他用例。）
     */
    /**
     * 把模拟玩家挪到牌桌旁。入局现在有服务端距离校验（防止改客户端在世界任意角落入局），
     * 而模拟玩家默认生成在关卡出生点、离测试结构很远，不挪过来会被直接拒绝。
     */
    /**
     * 游戏进行中有人「离开 / 掉线」：牌局要解散、**按判负结算**（赔其余两家各 门槛 / 2）、
     * 而且不能抛异常。
     *
     * <p><b>为什么不是"退还押注"</b>：局中走人若只退押注，就等于"输了就跑、一分不赔"，
     * 押注形同虚设。所以局中离开与超时一样按判负处理（见 {@code DDZSession#forfeit}），
     * 判负者押在池子里的原物先用于赔付。这个用例同时钉住金额：判负者净 -门槛、
     * 其余两家各净 +门槛/2，总量守恒。</p>
     *
     * <p>另一件事（原用例的本意）：这条路径上不能抛异常。{@code handleLeave} 会先清掉离开者的
     * 座位再调 {@code disband}，而 {@code disband} 遍历整个座位表退筹码——表里那个 null 直接让
     * {@code returnBet(null)} 抛 NPE，服务端线程会崩（日志里是 "Encountered an unexpected exception"）。
     * 单测覆盖不到这段（要 {@code ServerPlayer}），所以在这里用模拟玩家钉住。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void midGameLeaveForfeitsToOthers(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        DDZTestSupport.prepareBetting(manager, key, p1, p2, p3);
        int each = DDZTestSupport.countOf(p1, DDZTestSupport.CHIP);   // 三人起点相同
        try {
            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() == DDZGamePhase.WAITING) {
                helper.fail("三人入局后应已开局");
                return;
            }
            int per = session.requiredChips() / 2;

            // 离开者（含掉线走的同一条路）——这里就是曾经抛 NPE 的那一步
            session.handleLeave(p1, manager);

            if (manager.session(key) != null) {
                helper.fail("游戏中有人离开后牌局应解散");
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
                helper.fail("局中离开者应按判负赔出 " + (2 * per) + " 个（门槛 "
                    + session.requiredChips() + " / 2 各一份），实际净额 " + net[0]);
                return;
            }
            if (net[1] != per || net[2] != per) {
                helper.fail("其余两家应各得 " + per + " 个且各自的押注原样退回，实际 "
                    + net[1] + " / " + net[2]);
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /**
     * 开局前（还在等人）离开：只退<b>自己</b>那份押注，牌局留着继续等人。
     *
     * <p>与上面那条正好成对：判负只在牌局真的开始之后才生效。没有这条，
     * 把"局中离开"的实现顺手管到"等待中离开"就会变成"人还没凑齐就先赔两家"。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void waitingLeaveRefundsOwnStake(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, p2);
        int each = DDZTestSupport.countOf(p1, DDZTestSupport.CHIP);
        try {
            manager.join(p1, key);
            manager.join(p2, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.WAITING) {
                helper.fail("只坐两人时应停在等待中，实际 "
                    + (session == null ? "无会话" : session.phase().toString()));
                return;
            }
            if (DDZTestSupport.countOf(p1, DDZTestSupport.CHIP) != each - session.stake()) {
                helper.fail("入局应按本桌底注收走押注");
                return;
            }

            session.handleLeave(p1, manager);

            if (session.contains(p1)) {
                helper.fail("离开后不该还占着座位");
                return;
            }
            if (DDZTestSupport.countOf(p1, DDZTestSupport.CHIP) != each) {
                helper.fail("开局前离开应原样退还自己的押注（应为 " + each + " 个，实际 "
                    + DDZTestSupport.countOf(p1, DDZTestSupport.CHIP) + "）");
                return;
            }
            if (manager.session(key) == null || !session.contains(p2)) {
                helper.fail("还有人坐着时牌局不该解散");
                return;
            }
            if (DDZTestSupport.countOf(p2, DDZTestSupport.CHIP) != each - session.stake()) {
                helper.fail("留下的玩家押注仍在池子里，不该被误退");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            cleanup(manager, key, helper, p1, p2);
        }
    }

    private static ServerPlayer atTable(GameTestHelper helper, BlockPos relativeTable, ServerPlayer player) {
        var center = helper.absolutePos(relativeTable);
        player.teleportTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        return player;
    }

    /**
     * 押注的物品必须**原样**返还：附魔/自定义名等组件不能丢。
     *
     * <p>原实现只记物品注册名，返还时用 {@code new ItemStack(item, 1)} 重建，
     * 于是押一把附魔剑拿回来的是白板剑。这是静默的数据丢失（不会报错、界面也看不出来），
     * 只能靠断言固定：退还后要求物品仍是附了魔的那一把。</p>
     *
     * <p>同时断言"零和"：三家押注的总件数在退还后必须与开始时一致——
     * 早先的实现按类型重建 2 个发出去，3 份押注里有 1 份被静默销毁（每局净损失 1 个物品）。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void enchantedStakeReturnedIntact(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        // 钻石不可附魔，故临时把筹码白名单换成"钻石剑"，才能验证筹码在物品池里往返时组件不丢
        var savedChips = java.util.List.copyOf(ServerGameConfig.chipItems);
        ServerGameConfig.chipItems = java.util.List.of("minecraft:diamond_sword");

        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        try {
            // 备足门槛数量（门槛随底注配置变化，写死数字会失效）
            int swords = com.jokernan.craftycards.game.server.ServerGameConfig.requiredChips();
            for (ServerPlayer p : players) {
                p.getInventory().setItem(0, enchantedSword(helper, swords));
                p.getInventory().selected = 0;
            }
            DDZTestSupport.declareChip(manager, p1, key, Items.DIAMOND_SWORD);
            for (ServerPlayer p : players) manager.join(p, key);

            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            // 收走底注（1 把）进物品池
            for (ServerPlayer p : players) {
                if (DDZTestSupport.countOf(p, Items.DIAMOND_SWORD) != swords - 1) {
                    helper.fail("入局应只收走 1 把剑（剩 " + (swords - 1) + "），实际剩 "
                        + DDZTestSupport.countOf(p, Items.DIAMOND_SWORD));
                    return;
                }
                if (!hasEnchantedSword(p)) {
                    helper.fail("收走后剩下的剑应仍带附魔");
                    return;
                }
            }

            // 解散牌局 → 池子里的剑原样退还（组件必须完整）
            session.disband("测试收尾", manager);
            for (ServerPlayer p : players) {
                if (DDZTestSupport.countOf(p, Items.DIAMOND_SWORD) != swords) {
                    helper.fail("退还后应有 " + swords + " 把剑，实际 "
                        + DDZTestSupport.countOf(p, Items.DIAMOND_SWORD));
                    return;
                }
                if (!hasEnchantedSword(p)) {
                    helper.fail("退还的剑丢了附魔（组件未保留）——押什么就该还什么");
                    return;
                }
            }
            helper.succeed();
        } finally {
            ServerGameConfig.chipItems = savedChips;
            cleanup(manager, key, helper, p1, p2, p3);
        }
    }

    /** 若干把附了锋利 III 的钻石剑（用来验证筹码在物品池里往返时组件是否被保留）。 */
    private static ItemStack enchantedSword(GameTestHelper helper, int count) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD, count);
        var enchantments = helper.getLevel().registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        sword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        return sword;
    }

    /** 背包里是否有带附魔的钻石剑。 */
    private static boolean hasEnchantedSword(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.DIAMOND_SWORD) && st.isEnchanted()) return true;
        }
        return false;
    }

    /** 玩家背包里是否有斗地主扑克占位物品。 */
    private static boolean hasDdzCard(ServerPlayer player) {
        return player.getInventory().contains(st -> st.is(InitItems.DDZ_CARD.get()));
    }

    private static void cleanup(DDZTableManager manager, TableKey key, GameTestHelper helper,
                                ServerPlayer... players) {
        manager.remove(key);
        for (ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    /**
     * Shift+右键（WATCH 动作）= 未入局玩家的观战开关，走真实网络入口
     * {@code DDZTableManager.handle}。这是"非参局玩家看牌"的正规入口：
     * 不占座位、不必等坐满，因此公共服务器可以把"走近自动共享"关掉（默认就是关的）。
     */
    @GameTest(template = TEMPLATE)
    public static void watchActionTogglesWithoutTakingSeat(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer viewer = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, viewer);
        try {
            // 只有 1 人入局（还有空位）：观战不该占用空位
            manager.join(p1, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            if (session.isWatching(viewer)) {
                helper.fail("未操作前不应处于观战状态");
                return;
            }

            manager.handle(viewer, watchPayload(key));
            if (!session.isWatching(viewer)) {
                helper.fail("WATCH 动作应开启观战");
                return;
            }
            if (session.playerCount() != 1) {
                helper.fail("观战不该占用座位（应仍为 1 人），实际 " + session.playerCount());
                return;
            }

            manager.handle(viewer, watchPayload(key));
            if (session.isWatching(viewer)) {
                helper.fail("再次 WATCH 应停止观战");
                return;
            }

            // 超出距离：应被拒绝，不得进入观战
            ServerPlayer far = helper.makeMockServerPlayerInLevel();
            var center = helper.absolutePos(CORE);
            far.teleportTo(center.getX() + 200, center.getY(), center.getZ());
            manager.handle(far, watchPayload(key));
            if (session.isWatching(far)) {
                helper.fail("200 格外的玩家不该能观战（服务端缺少距离校验）");
                return;
            }
            helper.getLevel().getServer().getPlayerList().remove(far);
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            cleanup(manager, key, helper, p1, viewer);
        }
    }

    /** 构造一个 WATCH 动作包。 */
    private static com.jokernan.craftycards.network.payload.PlayerActionPayload watchPayload(TableKey key) {
        return new com.jokernan.craftycards.network.payload.PlayerActionPayload(
            key, com.jokernan.craftycards.network.payload.PlayerActionPayload.Action.WATCH, 0, List.of());
    }

    /** 远处的玩家（含改客户端伪造坐标）不得入局——服务端按距离与维度校验。 */
    @GameTest(template = TEMPLATE)
    public static void distantPlayerRejected(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer near = atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, near);
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        DDZTestSupport.stockChips(far);   // 备足筹码，确保唯一被拒的原因是距离
        far.teleportTo(helper.absolutePos(CORE).getX() + 200, helper.absolutePos(CORE).getY(),
            helper.absolutePos(CORE).getZ());
        try {
            manager.join(far, key);
            DDZSession session = manager.session(key);
            if (session != null && session.contains(far)) {
                helper.fail("距离 200 格的玩家不应入局（服务端缺少距离校验）");
                return;
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            cleanup(manager, key, helper, near, far);
        }
    }

    private static DDZGamePhase phaseOf(DDZTableManager manager, TableKey key) {
        DDZSession session = manager.session(key);
        return session == null ? null : session.phase();
    }
}
