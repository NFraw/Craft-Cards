package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 斗地主 GameTest 的公共前置操作。
 *
 * <p>集中这里的原因：入局现在有两道前置——**筹码必须先声明**、**背包里筹码数量要够**。
 * 每个用例各自写一遍既啰嗦又容易漏，漏了就会得到"右键没反应"式的失败，很难看出真正原因。</p>
 */
final class DDZTestSupport {
    /** 测试统一使用的筹码物品。 */
    static final Item CHIP = Items.DIAMOND;

    private DDZTestSupport() {}

    /** 把玩家挪到牌桌旁：入局/观战/声明筹码都有服务端距离校验，而模拟玩家默认生成在关卡出生点。 */
    static ServerPlayer atTable(GameTestHelper helper, BlockPos relativeTable, ServerPlayer player) {
        var center = helper.absolutePos(relativeTable);
        player.teleportTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        return player;
    }

    /** 给玩家备足筹码：按配置要求 + 2 个余量（余量供地主在"农民赢"时赔付）。 */
    static void stockChips(ServerPlayer player) {
        stockChips(player, com.jokernan.craftycards.game.server.ServerGameConfig.requiredChips() + 2);
    }

    static void stockChips(ServerPlayer player, int count) {
        stockChips(player, CHIP, count);
    }

    /** 备指定物品的筹码——筹码类型是被声明的，玩家必须持有**那一种**物品。 */
    static void stockChips(ServerPlayer player, Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
    }

    /**
     * 配置本桌筹码：走**真实网络入口**，与配置界面同一条路——
     * 先 {@code OPEN_TABLE_CONFIG}（成为房主、种入本桌默认值、拿配置锁），
     * 再 {@code SAVE_TABLE_CONFIG}（只改筹码类型，其余三项保持原样）。
     *
     * <p>为什么先 OPEN 再读方块实体：本桌的门槛/底注是"第一次被用到时从全局默认值种入"的，
     * 不先 OPEN 就读到的是字段初值（0 / 1），保存回去等于把种入的那份又覆盖掉——
     * 用例想要的是"只换筹码，别动别的"。</p>
     */
    static void declareChip(DDZTableManager manager, ServerPlayer host, TableKey key, Item chip) {
        manager.handle(host, new PlayerActionPayload(
            key, PlayerActionPayload.Action.OPEN_TABLE_CONFIG, 0, List.of()));
        DDZTableBlockEntity table = tableEntity(key, host);
        if (table == null) return;   // 用例自己会断言后续行为，这里不抛
        manager.handle(host, new PlayerActionPayload(
            key, PlayerActionPayload.Action.SAVE_TABLE_CONFIG, table.stake(),
            List.of(table.entryCount()), itemId(chip), table.chipsEnabled()));
    }

    /** 本桌的方块实体（测试里直接读，用来确认"保存后桌上到底存了什么"）。 */
    static DDZTableBlockEntity tableEntity(TableKey key, ServerPlayer any) {
        return any.serverLevel().getBlockEntity(key.pos()) instanceof DDZTableBlockEntity t ? t : null;
    }

    /** 物品 → 注册名（配置界面里筹码类型就是注册名字符串）。 */
    static String itemId(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * 备好筹码并给本桌配上 {@link #CHIP}（走真实的配置界面入口），一步到位。
     */
    static void prepareBetting(DDZTableManager manager, TableKey key, ServerPlayer... players) {
        forceBettingDefaults();
        for (ServerPlayer p : players) stockChips(p);
        declareChip(manager, players[0], key, CHIP);
    }

    /**
     * 钉住赌注类用例的前提：<b>全局闸门开着 + 全局默认底注 1 + 门槛自动</b>。
     *
     * <p>为什么必须显式钉：{@code server.json} 里的 {@code chipsRequired} 与 {@code chipStake}
     * 会从开发机的配置文件里读出来（并且现在真的会持久化）——把筹码关掉、或把底注调成 2，
     * 就会得到一堆与代码无关的失败：门槛变 0、"入局应只收走 1 个"变成收走 2 个、
     * 赔付额超出背包导致净额断言不过。用例自己声明前提，配置怎么改都不影响判定。</p>
     *
     * <p>这些值同时是<b>本桌配置的种子</b>：桌子第一次被用到时会把它们种进方块实体
     * （见 {@code DDZTableBlockEntity#ensureSeeded}），之后只认自己的值。所以钉在这里
     * 也等于钉住了本桌的底注与门槛。</p>
     *
     * <p>底注钉 1 是因为若干断言按"入局收走 1 个""地主赔得起"写死（{@code requiredChips()}
     * 只管门槛，不管实收几个）。要让本套用例支持其他底注，得把这些断言改成按
     * 底注派生并给足赔付余量——那是另一件事，与筹码开关无关。</p>
     */
    static void forceBettingDefaults() {
        com.jokernan.craftycards.game.server.ServerGameConfig.chipsRequired = true;
        com.jokernan.craftycards.game.server.ServerGameConfig.chipStake = 1;
        // 门槛也必须钉成"自动"：房主可能在开发机的 server.json 里写死了自定义门槛
        com.jokernan.craftycards.game.server.ServerGameConfig.chipEntryCount = 0;
    }

    /** 玩家背包（含副手与护甲槽）里某物品的总数。 */
    static int countOf(ServerPlayer player, Item item) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) total += st.getCount();
        }
        return total;
    }

    /** 收集收尾：先摘桌再移除模拟玩家（移除玩家会触发登出事件，此时会话还在就会走 disband）。 */
    static void cleanup(DDZTableManager manager, TableKey key, GameTestHelper helper,
                        ServerPlayer... players) {
        manager.remove(key);
        for (ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    /** 只出单张的打法所需的步数上限（每轮至少消一张牌，正常远小于此）。 */
    private static final int MAX_SINGLE_CARD_STEPS = 400;

    /**
     * 只用单张把一局走到底：领先时出最小的一张；跟牌时只在"上家出的是单张"且压得过时压，
     * 否则过牌。每轮至少消一张牌，故必然推进到结算。
     *
     * <p>写成公共方法是因为"只想把牌局走完"的用例不止一个，而这段循环最容易写错
     * （漏掉"不能过牌"的情形、或造出非法牌型）。</p>
     *
     * @return 是否在步数上限内进入结算；未进入时已调用 {@code helper.fail}
     */
    static boolean playOutWithSingleCards(GameTestHelper helper, DDZSession session, ServerPlayer[] players) {
        int steps = 0;
        while (session.phase() == DDZGamePhase.PLAYING && ++steps < MAX_SINGLE_CARD_STEPS) {
            ServerPlayer who = players[session.currentSeat()];
            var view = session.snapshotFor(who);
            java.util.List<Integer> hand = new java.util.ArrayList<>(view.myHand());
            if (hand.isEmpty()) break;
            boolean leading = view.lastPlayedBy() < 0 || view.lastPlayedBy() == view.myIndex();
            boolean singleOnTable = view.lastPlayedType() == DDZCardType.SINGLE
                && !view.lastPlayedCards().isEmpty();
            int above = leading ? -1
                : singleOnTable ? DDZEngine.getRank(view.lastPlayedCards().get(0)) : Integer.MAX_VALUE;
            int move = -1, bestRank = Integer.MAX_VALUE;
            for (int card : hand) {
                int rank = DDZEngine.getRank(card);
                if (rank > above && rank < bestRank) {
                    bestRank = rank;
                    move = card;
                }
            }
            if (move < 0) session.handlePass(who);
            else session.handlePlay(who, java.util.List.of(move));
        }
        if (session.phase() != DDZGamePhase.SETTLED) {
            helper.fail("只出单张的牌局应在 " + MAX_SINGLE_CARD_STEPS + " 步内结束，实际 " + session.phase());
            return false;
        }
        return true;
    }

    /** 本模组的 GameTest 统一使用 holder 注解里的命名空间常量。 */
    static String modId() {
        return CCReference.MOD_ID;
    }
}
