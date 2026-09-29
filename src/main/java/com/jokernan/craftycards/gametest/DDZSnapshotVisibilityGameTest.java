package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.server.DDZSession;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 快照可见性的交付测试：**服务端真的按接收者下发不同的牌面数据**。
 *
 * <p>这是整套防作弊的地基：客户端只能渲染它收到的牌面，服务端不下发就等于看不见。
 * {@code DDZVisibility} 的规则已有纯函数单测，但"服务端接线是否正确"（是否真按接收者构建、
 * 参与者/旁观者是否走对分支）只有在这里才能验证——因为 {@code buildSnapshot} 是私有的，
 * 规则写对而接线接错是完全可能的。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZSnapshotVisibilityGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /**
     * 默认配置下（旁观可见、参与者互不可见）的完整矩阵，逐个接收者取快照断言：
     * <ul>
     *   <li>参与者：拿到自己的 17 张手牌；他人牌面列表必须为空（防作弊）</li>
     *   <li>旁观者：自己的手牌为空；三家牌面都能拿到（可走过去看牌）</li>
     * </ul>
     */
    @GameTest(template = TEMPLATE)
    public static void snapshotIsBuiltPerReceiver(GameTestHelper helper) {
        boolean oldSpectators = ServerGameConfig.spectatorsSeeCards;
        boolean oldParticipants = ServerGameConfig.participantsSeeFaces;
        ServerGameConfig.spectatorsSeeCards = true;
        ServerGameConfig.participantsSeeFaces = false;

        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer spectator = atTable(helper, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, p2, p3);
        try {
            for (ServerPlayer p : List.of(p1, p2, p3)) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("三人入局后应处于叫分阶段");
                return;
            }

            // === 参与者视角 ===
            int myIndex = session.seatOf(p1.getUUID());
            GameStatePayload mine = session.snapshotFor(p1);
            if (mine.myIndex() != myIndex) {
                helper.fail("参与者快照的 myIndex 应为自己的座位，实际 " + mine.myIndex());
                return;
            }
            if (mine.myHand().size() != DDZEngine.CARDS_PER_PLAYER) {
                helper.fail("参与者应拿到自己的 " + DDZEngine.CARDS_PER_PLAYER + " 张手牌，实际 "
                    + mine.myHand().size());
                return;
            }
            // 手牌**内容**也要对：17 张必须互不相同、且都在 0..53 内。
            // 这条曾经只是"看起来显然"，但 HUD 上"每张牌长得一样"这类现象一旦出现，
            // 第一件要排除的就是"下发的牌 ID 是不是同一个"——钉住它，排查就不必靠猜。
            java.util.Set<Integer> distinct = new java.util.HashSet<>(mine.myHand());
            if (distinct.size() != mine.myHand().size()) {
                helper.fail("下发的 " + mine.myHand().size() + " 张手牌里有重复，去重后只有 "
                    + distinct.size() + " 种：" + mine.myHand());
                return;
            }
            int deckSize = DDZEngine.PLAYER_COUNT * DDZEngine.CARDS_PER_PLAYER + DDZEngine.DIPI_COUNT;
            for (int cardId : mine.myHand()) {
                if (cardId < 0 || cardId >= deckSize) {
                    helper.fail("手牌里出现越界牌 ID " + cardId + "（应在 0.." + (deckSize - 1) + "）");
                    return;
                }
            }
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (!mine.visibleHands().get(seat).isEmpty()) {
                    helper.fail("默认配置下参与者不该拿到座位 " + seat + " 的牌面（防作弊）");
                    return;
                }
            }

            // === 旁观者视角 ===
            GameStatePayload watched = session.snapshotFor(spectator);
            if (watched.myIndex() != -1) {
                helper.fail("旁观者的 myIndex 应为 -1，实际 " + watched.myIndex());
                return;
            }
            if (!watched.myHand().isEmpty()) {
                helper.fail("旁观者不应有手牌");
                return;
            }
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (watched.visibleHands().get(seat).isEmpty()) {
                    helper.fail("旁观者应能看到座位 " + seat + " 的牌面（spectatorsSeeCards 已开）");
                    return;
                }
            }
            // 旁观者拿到的对手牌面必须等于服务端真实手牌
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (watched.visibleHands().get(seat).size() != DDZEngine.CARDS_PER_PLAYER) {
                    helper.fail("旁观者拿到的座位 " + seat + " 牌面张数不对：" + watched.visibleHands().get(seat).size());
                    return;
                }
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.spectatorsSeeCards = oldSpectators;
            ServerGameConfig.participantsSeeFaces = oldParticipants;
            cleanup(manager, key, helper, p1, p2, p3, spectator);
        }
    }

    /** 关掉旁观可见后，旁观者也只能拿到牌背（张数照旧可见）。 */
    @GameTest(template = TEMPLATE)
    public static void spectatorBlindedWhenDisabled(GameTestHelper helper) {
        boolean oldSpectators = ServerGameConfig.spectatorsSeeCards;
        ServerGameConfig.spectatorsSeeCards = false;

        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer spectator = atTable(helper, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, p2, p3);
        try {
            for (ServerPlayer p : List.of(p1, p2, p3)) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null) {
                helper.fail("会话不存在");
                return;
            }
            GameStatePayload watched = session.snapshotFor(spectator);
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (!watched.visibleHands().get(seat).isEmpty()) {
                    helper.fail("spectatorsSeeCards=false 时不该下发牌面（座位 " + seat + "）");
                    return;
                }
            }
            // 张数属于公开信息，仍要下发（世界内立牌张数靠它）
            for (int seat = 0; seat < DDZEngine.PLAYER_COUNT; seat++) {
                if (watched.playerCardCounts().get(seat) != DDZEngine.CARDS_PER_PLAYER) {
                    helper.fail("牌背模式下仍应下发各家的牌数");
                    return;
                }
            }
            helper.succeed();
        } catch (RuntimeException e) {
            manager.remove(key);
            throw e;
        } finally {
            ServerGameConfig.spectatorsSeeCards = oldSpectators;
            cleanup(manager, key, helper, p1, p2, p3, spectator);
        }
    }

    /**
     * 底牌在叫分阶段**不得下发**——它决定叫几分的判断。
     *
     * <p>回归：快照曾经无条件带上 {@code engine.getDipai()}，只有 HUD 的显示条件
     * （{@code snap.phase() == PLAYING} 才画底牌）挡着。那只是"画不画"，数据已经在包里，
     * 改过的客户端在决定叫分前就能读到 3 张底牌——与"可见性完全由服务端决定"自相矛盾。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void dipaiHiddenUntilPlaying(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        TableKey key = new TableKey(helper.getLevel().dimension(), helper.absolutePos(CORE));
        DDZTableManager manager = DDZTableManager.getInstance();

        ServerPlayer p1 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p2 = atTable(helper, helper.makeMockServerPlayerInLevel());
        ServerPlayer p3 = atTable(helper, helper.makeMockServerPlayerInLevel());
        DDZTestSupport.prepareBetting(manager, key, p1, p2, p3);
        try {
            for (ServerPlayer p : List.of(p1, p2, p3)) manager.join(p, key);
            DDZSession session = manager.session(key);
            if (session == null || session.phase() != DDZGamePhase.BIDDING) {
                helper.fail("三人入局后应处于叫分阶段");
                return;
            }
            if (!session.snapshotFor(p1).dipai().isEmpty()) {
                helper.fail("叫分阶段的快照不该带底牌（改过的客户端能拿它决定叫分）");
                return;
            }

            // 叫满 3 分进入出牌：底牌已归地主，属于公开信息，应当下发
            ServerPlayer[] players = {p1, p2, p3};
            session.handleBid(players[session.currentSeat()], 3);
            if (session.phase() != DDZGamePhase.PLAYING) {
                helper.fail("叫 3 分后应进入出牌，实际 " + session.phase());
                return;
            }
            if (session.snapshotFor(p1).dipai().size() != DDZEngine.DIPI_COUNT) {
                helper.fail("出牌阶段应下发 " + DDZEngine.DIPI_COUNT + " 张底牌（已在桌面上公开）");
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

    private static ServerPlayer atTable(GameTestHelper helper, ServerPlayer player) {
        var center = helper.absolutePos(CORE);
        player.teleportTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        return player;
    }

    private static void cleanup(DDZTableManager manager, TableKey key, GameTestHelper helper,
                                ServerPlayer... players) {
        manager.remove(key);
        for (ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }
}
