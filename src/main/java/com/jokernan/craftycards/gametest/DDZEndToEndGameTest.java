package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主全流程交付测试：合成牌桌 → 放置 → 三人入局下注 → 叫分 → 出牌至结束 → 结算与回收。
 *
 * <p>这是唯一一条把"配方 → 方块 → 会话 → 引擎 → 结算 → 背包"整条链串起来的自动化测试。
 * 各环节单独都有覆盖（配方加载/方块放置/引擎规则/会话开局），但"串起来跑一整局"只有这里做，
 * 而交付时最可能出问题的恰恰是环节之间（例如结算时赌注没转移、牌没回收）。
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZEndToEndGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);
    /** 出牌循环的硬上限：每轮至少消一张牌，正常远小于此值；设上限是为了万一死循环时快速失败而不是挂住。 */
    private static final int MAX_ACTIONS = 400;
    /**
     * 每位玩家入局前持有的筹码数 = <b>从配置派生</b>的门槛 + 2 个余量。
     *
     * <p>写死数字会在"底注/门槛"调整后失效（本用例就因此失败过一次）；从
     * {@link ServerGameConfig#requiredChips()} 派生才能跟着配置走。
     * 余量的作用：结算赔付可能超过押注额，有余额才赔得出。</p>
     */
    private static int startChips() {
        return com.jokernan.craftycards.game.server.ServerGameConfig.requiredChips() + 2;
    }

    /**
     * 完整一局：按配方合成牌桌 → 放下 → 三人（各持 1 铁锭下注）入局 → 叫 3 分 →
     * 逐轮出牌直到有人手牌出空 → 校验结算结果、赌注归属、扑克回收、会话摘除。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void fullGameFromCraftingToSettlement(GameTestHelper helper) {
        DDZTestSupport.forceBettingDefaults();
        // === 1) 合成牌桌：走真实配方（输入按 recipe/blocks/ddz_table.json 的形状） ===
        ItemStack tableItem = craftDdzTable(helper);
        if (tableItem == null) return;

        // === 2) 放置 ===
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        // === 3) 三人入局：先声明本局筹码（钻石）并备足数量 ===
        ServerPlayer p1 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());
        ServerPlayer[] players = {p1, p2, p3};
        for (ServerPlayer p : players) DDZTestSupport.stockChips(p, startChips());
        DDZTestSupport.declareChip(manager, p1, key, DDZTestSupport.CHIP);

        try {
            for (ServerPlayer p : players) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("三人入局后会话不存在");
                return;
            }
            if (session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("三人坐满应进入叫分，实际 " + session.phase());
                return;
            }
            // 筹码：每人被收走 1 个进物品池，扑克占位物品已发放
            for (ServerPlayer p : players) {
                if (DDZTestSupport.countOf(p, DDZTestSupport.CHIP) != startChips() - 1) {
                    helper.fail("入局应把 1 个筹码收进物品池（每人余 " + (startChips() - 1) + "），实际 "
                        + DDZTestSupport.countOf(p, DDZTestSupport.CHIP));
                    return;
                }
                if (DDZTestSupport.countOf(p, InitItems.DDZ_CARD.get()) != 1) {
                    helper.fail("入局应发放恰好 1 张斗地主扑克");
                    return;
                }
            }

            // === 4) 叫分：由当前叫分者叫满 3 分，直接定地主 ===
            ServerPlayer bidder = players[session.currentSeat()];
            session.handleBid(bidder, 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }
            int landlord = session.landlordSeat();
            if (landlord < 0) {
                helper.fail("叫 3 分后应定出地主");
                return;
            }
            if (session.handSize(landlord) != DDZEngine.CARDS_PER_PLAYER + DDZEngine.DIPI_COUNT) {
                helper.fail("地主应收底牌（应为 " + (DDZEngine.CARDS_PER_PLAYER + DDZEngine.DIPI_COUNT)
                    + " 张），实际 " + session.handSize(landlord));
                return;
            }
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (seat != landlord && session.handSize(seat) != DDZEngine.CARDS_PER_PLAYER) {
                    helper.fail("农民应保持 " + DDZEngine.CARDS_PER_PLAYER + " 张");
                    return;
                }
            }

            // === 5) 出牌至结束：能压就压最小的单张，压不过就过 ===
            int actions = 0;
            while (session.phase() == DDZGamePhase.PLAYING) {
                if (++actions > MAX_ACTIONS) {
                    helper.fail("出牌循环超过 " + MAX_ACTIONS + " 步仍未结束，疑似卡死");
                    return;
                }
                int seat = session.currentSeat();
                ServerPlayer who = players[seat];
                GameStatePayload view = session.snapshotFor(who);
                List<Integer> hand = new ArrayList<>(view.myHand());
                if (hand.isEmpty()) {
                    helper.fail("座位 " + seat + " 手牌为空但牌局未结算");
                    return;
                }
                // handlePlay/handlePass 返回 void（失败时只给玩家发提示），故改为校验"状态确实推进了"：
                // 要么牌局进入下一阶段，要么轮到下一家，要么自己的手牌少了一张。
                int move = chooseMove(view, hand);
                int beforeSeat = seat;
                int beforeHand = hand.size();
                DDZGamePhase beforePhase = session.phase();
                if (move < 0) {
                    session.handlePass(who);
                } else {
                    session.handlePlay(who, List.of(move));
                }
                boolean progressed = session.phase() != beforePhase
                    || session.currentSeat() != beforeSeat
                    || session.handSize(seat) < beforeHand;
                if (!progressed) {
                    helper.fail("动作被拒绝且状态未变：座位 " + seat + " 手牌 " + beforeHand
                        + " 张，动作=" + (move < 0 ? "过牌" : ("出单张 " + move)));
                    return;
                }
            }

            // === 6) 结算校验 ===
            if (session.phase() != DDZGamePhase.SETTLED) {
                helper.fail("出牌结束应进入结算，实际 " + session.phase());
                return;
            }
            int winnerTeam = session.snapshotFor(p1).winnerTeam();
            boolean landlordWon = winnerTeam == landlord;
            if (!landlordWon && winnerTeam != -2) {
                helper.fail("胜负判定异常：winnerTeam=" + winnerTeam + " 地主座位=" + landlord);
                return;
            }

            // 赌注按文档的净额转移：地主赢 +2 / 农民各 -1；农民赢 地主 -2 / 农民各 +1。
            // 断言"净额"而不是绝对数量：绝对数量的断言会把错误行为固化成期望值
            // （旧实现下农民赢了净值是 0，而当时的测试恰好也断言了 0，等于把 bug 写进了测试）。
            int[] net = new int[3];
            for (int i = 0; i < 3; i++) {
                net[i] = DDZTestSupport.countOf(players[i], DDZTestSupport.CHIP) - startChips();
            }
            int total = 0;
            for (ServerPlayer p : players) total += DDZTestSupport.countOf(p, DDZTestSupport.CHIP);
            if (total != startChips() * 3) {
                helper.fail("筹码总量应守恒为 " + (startChips() * 3) + "，实际 " + total
                    + "——有物品被静默销毁或凭空产生");
                return;
            }
            // 期望值按计分公式推导：每农民 = 底分 × 倍数 × 底注（含炸弹与春天），地主为其两倍。
            // 不能写死 ±1/±2——本局可能出现炸弹或春天，倍数会变。
            int perFarmer = session.scorePerFarmer();
            if (perFarmer <= 0) {
                helper.fail("每农民得失应为正数，实际 " + perFarmer);
                return;
            }
            for (int i = 0; i < 3; i++) {
                boolean isLandlord = i == landlord;
                int magnitude = isLandlord ? perFarmer * 2 : perFarmer;
                int expected = landlordWon == isLandlord ? magnitude : -magnitude;
                if (net[i] != expected) {
                    helper.fail("座位 " + i + "（" + (isLandlord ? "地主" : "农民") + "）净额应为 " + expected
                        + "（每农民 " + perFarmer + " × " + (isLandlord ? 2 : 1) + "），实际 " + net[i]
                        + "，倍数 " + session.snapshotFor(p1).betMulti());
                    return;
                }
            }

            // 扑克回收 + 会话摘除
            for (ServerPlayer p : players) {
                if (DDZTestSupport.countOf(p, InitItems.DDZ_CARD.get()) != 0) {
                    helper.fail("结算后应回收斗地主扑克");
                    return;
                }
            }
            if (manager.session(key) != null) {
                helper.fail("结算后会话应从管理器摘除");
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
     * 服务端右键牌桌不得崩溃 —— 这是本项目头号坑的专项测试。
     *
     * <p>{@code BlockDDZTable.useWithoutItem} 里有一行 {@code ClientDDZData.getInstance()...}，
     * 而该类是 {@code @OnlyIn(CLIENT)}。若方法体在服务端被解析（没有 {@code isClientSide} 守卫，
     * 或写成 {@code instanceof}/{@code new}/字段访问），JVM 会在类链接时就去加载客户端类，
     * 服务端直接崩 "Attempted to load class ... for invalid dist DEDICATED_SERVER"。</p>
     *
     * <p>本用例在**专用服务器环境**（GameTest 就是这个环境，dist cleaner 生效）直接调用该方法，
     * 确认：① 不抛异常；② 返回 SUCCESS 但不自行入局——真正的入局是由客户端发 JOIN 包触发的，
     * 方块本身不该在服务端动手（否则会出现一次右键入局两次的重复申请）。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void serverSideRightClickIsSafe(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();
        ServerPlayer player = DDZTestSupport.atTable(helper, CORE, helper.makeMockServerPlayerInLevel());

        try {
            var pos = helper.absolutePos(CORE);
            var state = helper.getBlockState(CORE);
            var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP, pos, false);
            net.minecraft.world.InteractionResult result;
            try {
                // 原版 ServerPlayerGameMode 用的就是这个公开分发入口
                result = state.useWithoutItem(helper.getLevel(), player, hit);
            } catch (Throwable t) {
                helper.fail("服务端调用 useWithoutItem 抛异常（疑似链接到客户端类）：" + t);
                return;
            }
            if (result != net.minecraft.world.InteractionResult.SUCCESS) {
                helper.fail("服务端右键牌桌应返回 SUCCESS，实际 " + result);
                return;
            }
            DDZSession session = manager.session(key);
            if (session != null && session.contains(player)) {
                helper.fail("方块本身不应在服务端入局——入局由客户端发 JOIN 包触发");
                return;
            }
            helper.succeed();
        } finally {
            manager.remove(key);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /**
     * 按配方合成一张牌桌；失败时已调用 {@code helper.fail} 并返回 null。
     * 输入按 {@code recipe/blocks/ddz_table.json} 的形状：{@code " C " / "PPP" / "S S"}。
     */
    private static ItemStack craftDdzTable(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        var found = manager.byKey(ResourceLocation.parse("crafty_cards:blocks/ddz_table"));
        if (found.isEmpty()) {
            helper.fail("找不到牌桌配方 crafty_cards:blocks/ddz_table");
            return null;
        }
        RecipeHolder<?> holder = found.get();
        if (!(holder.value() instanceof CraftingRecipe recipe)) {
            helper.fail("牌桌配方不是合成配方：" + holder.value().getClass());
            return null;
        }
        List<ItemStack> grid = new ArrayList<>();
        grid.add(ItemStack.EMPTY);                                   // " C "
        grid.add(new ItemStack(Items.GREEN_CARPET));
        grid.add(ItemStack.EMPTY);
        grid.add(new ItemStack(Items.OAK_PLANKS));                   // "PPP"
        grid.add(new ItemStack(Items.OAK_PLANKS));
        grid.add(new ItemStack(Items.OAK_PLANKS));
        grid.add(new ItemStack(Items.STICK));                        // "S S"
        grid.add(ItemStack.EMPTY);
        grid.add(new ItemStack(Items.STICK));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(input, helper.getLevel())) {
            helper.fail("按配方形状摆放（绿地毯 + 木板×3 + 木棍×2）却匹配失败");
            return null;
        }
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        if (!result.is(InitItems.DDZ_TABLE_ITEM.get())) {
            helper.fail("配方产物不是牌桌物品：" + result);
            return null;
        }
        return result;
    }

    /**
     * 选一手牌：领先或压不过就出最小的单张，否则返回 -1 表示过牌。
     * 只用单张出牌，保证每一步都是合法牌型；每轮至少消一张牌，故牌局必然推进到结束。
     */
    private static int chooseMove(GameStatePayload view, List<Integer> hand) {
        boolean leading = view.lastPlayedBy() < 0 || view.lastPlayedBy() == view.myIndex();
        if (leading) return lowestByRank(hand, -1);
        // 只有上家出单张时才尝试压（其它牌型一律过牌，避免构造复杂牌型）
        if (view.lastPlayedType() != DDZCardType.SINGLE || view.lastPlayedCards().isEmpty()) return -1;
        int lastRank = DDZEngine.getRank(view.lastPlayedCards().get(0));
        return lowestByRank(hand, lastRank);
    }

    /** 返回手中 rank 大于 {@code aboveRank} 的最小牌；没有则返回 -1。 */
    private static int lowestByRank(List<Integer> hand, int aboveRank) {
        int best = -1, bestRank = Integer.MAX_VALUE;
        for (int card : hand) {
            int rank = DDZEngine.getRank(card);
            if (rank > aboveRank && rank < bestRank) {
                bestRank = rank;
                best = card;
            }
        }
        return best;
    }

    /** 玩家背包中某物品的总数（含副手与护甲槽）。 */
    private static int countOf(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        NonNullList<ItemStack> items = player.getInventory().items;
        for (ItemStack st : items) {
            if (st.is(item)) total += st.getCount();
        }
        for (ItemStack st : player.getInventory().offhand) {
            if (st.is(item)) total += st.getCount();
        }
        return total;
    }

    private static void cleanup(DDZTableManager manager, TableKey key, GameTestHelper helper,
                                ServerPlayer... players) {
        DDZTestSupport.cleanup(manager, key, helper, players);
    }
}
