package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 计分规则测试（底分 × 倍数，按市面常见规则）。
 *
 * <p>规则：底分 = 叫分；倍数 = {@code 2^(炸弹+火箭数)}，春天或反春天再翻一倍。
 * 这些规则靠"打一局出来"验证——用真实引擎把局面推进到对应终局，比直接摆弄内部字段更能
 * 反映真实行为（也顺带覆盖"统计是否真的在出牌路径上累加"）。</p>
 */
class DDZScoringTest {
    /** 防止构造局面时死循环。 */
    private static final int MAX_STEPS = 500;

    /** 开局并把叫分定为 3 分（底分 3）。 */
    private static DDZEngine startGame() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int first = engine.getCurrentPlayerIndex();
        assertTrue(engine.bid(first, 3), "叫 3 分应成功");
        assertEquals(DDZGamePhase.PLAYING, engine.getPhase());
        return engine;
    }

    /** 未结算时的倍数只由炸弹决定：无炸弹 = 1，底分 3 → 每农民 3 分。 */
    @Test
    void baseScoreEqualsBidAndNoBombsMeans1x() {
        DDZEngine engine = startGame();
        assertEquals(3, engine.getBaseScore(), "底分应等于叫分");
        assertEquals(0, engine.getBombCount());
        assertEquals(1, engine.getMultiplier(), "无炸弹且未结算时应为 1 倍");
        assertEquals(3, engine.getScorePerFarmer(), "3 分 × 1 倍 = 每农民 3 分");
    }

    /**
     * 春天：地主赢且两个农民一张牌都没出过 → 倍数翻倍。
     * 构造：地主每次出一张单牌，两个农民一直过牌，直到地主出完。
     */
    @Test
    void springDoublesMultiplier() {
        DDZEngine engine = startGame();
        int landlord = engine.getLandlordIndex();

        int steps = 0;
        while (engine.getPhase() == DDZGamePhase.PLAYING) {
            assertTrue(++steps < MAX_STEPS, "局面未在限定步数内结束");
            if (engine.getCurrentPlayerIndex() == landlord) {
                List<Integer> hand = engine.getPlayerHand(landlord);
                assertTrue(engine.playCards(landlord, List.of(hand.get(0))), "地主应能出单张");
            } else {
                assertTrue(engine.pass(engine.getCurrentPlayerIndex()), "农民应能过牌");
            }
        }

        assertEquals(DDZGamePhase.SETTLED, engine.getPhase());
        assertEquals(landlord, engine.getWinnerTeam(), "地主应获胜");
        assertTrue(engine.isSpring(), "农民一张未出，应判定春天");
        assertFalse(engine.isAntiSpring(), "春天与反春天互斥");
        assertEquals(2, engine.getMultiplier(), "春天翻倍");
        assertEquals(6, engine.getScorePerFarmer(), "3 分 × 2 倍 = 每农民 6 分");
    }

    /**
     * 反春天：农民赢且地主只出了开局那一手 → 倍数翻倍。
     *
     * <p>构造要点：地主开局打一张**低牌**，并挑一个手里有更大牌的农民。之后该农民一直领出
     * （两连过后牌权回到他手上，领出时无需压牌），地主与另一农民一直过牌——
     * 于是地主只出了那一手。</p>
     */
    @Test
    void antiSpringDoublesMultiplier() {
        DDZEngine engine = null;
        int landlord = -1;
        int farmer = -1;
        int landlordCard = -1;
        int farmerCard = -1;
        for (int attempt = 0; attempt < 200 && engine == null; attempt++) {
            DDZEngine candidate = startGame();
            int l = candidate.getLandlordIndex();
            // 手牌已排序，取第一张当"低牌"；只要有农民能压过它就能构造出来
            int lead = candidate.getPlayerHand(l).get(0);
            int leadRank = DDZEngine.getRank(lead);
            for (int f = 0; f < DDZEngine.PLAYER_COUNT; f++) {
                if (f == l) continue;
                for (int c : candidate.getPlayerHand(f)) {
                    if (DDZEngine.getRank(c) > leadRank) {
                        engine = candidate;
                        landlord = l;
                        farmer = f;
                        landlordCard = lead;
                        farmerCard = c;
                        break;
                    }
                }
                if (engine != null) break;
            }
        }
        assumeTrue(engine != null, "200 次洗牌都没构造出「地主低牌 + 农民能压」的开局，跳过（非失败）");

        // 地主只出这一手
        assertTrue(engine.playCards(landlord, List.of(landlordCard)), "地主应能打出这张单牌");

        boolean needBeat = true;   // 农民第一次跟牌必须压过地主那张
        int steps = 0;
        while (engine.getPhase() == DDZGamePhase.PLAYING) {
            assertTrue(++steps < MAX_STEPS, "局面未在限定步数内结束");
            int current = engine.getCurrentPlayerIndex();
            if (current == landlord) {
                assertTrue(engine.pass(landlord), "地主应能过牌");
            } else if (current == farmer) {
                int play = needBeat ? farmerCard : engine.getPlayerHand(farmer).get(0);
                assertTrue(engine.playCards(farmer, List.of(play)),
                    "农民应能出牌（" + (needBeat ? "压过上家" : "领出") + "）");
                needBeat = false;
            } else {
                assertTrue(engine.pass(current), "另一农民应能过牌");
            }
        }

        assertEquals(DDZGamePhase.SETTLED, engine.getPhase());
        assertEquals(-2, engine.getWinnerTeam(), "农民应获胜");
        assertTrue(engine.isAntiSpring(), "地主只出了一手，应判定反春天");
        assertFalse(engine.isSpring(), "春天与反春天互斥");
        assertEquals(2, engine.getMultiplier(), "反春天翻倍");
        assertEquals(6, engine.getScorePerFarmer(), "3 分 × 2 倍");
    }

    /**
     * 炸弹每出现一次翻一倍；炸弹与春天叠加。
     *
     * <p>洗牌后不保证某一手有炸弹，故重试若干次找到一个起手带炸弹的地主；
     * 找不到就跳过（不误报失败）。这样既验证了真实路径，又不会偶发红。</p>
     */
    @Test
    void eachBombDoublesMultiplier() {
        DDZEngine engine = null;
        List<Integer> bombCards = null;
        for (int attempt = 0; attempt < 200 && engine == null; attempt++) {
            DDZEngine candidate = startGame();
            int landlord = candidate.getLandlordIndex();
            bombCards = findBomb(candidate, landlord);
            if (bombCards != null) engine = candidate;
        }
        assumeTrue(engine != null, "200 次洗牌都没给地主发到炸弹，跳过（非失败）");

        int landlord = engine.getLandlordIndex();
        assertEquals(1, engine.getMultiplier(), "出炸弹前为 1 倍");
        // 炸弹必须四张一起出（只出一张是单张，不是炸弹）
        assertTrue(engine.playCards(landlord, bombCards), "应能打出炸弹");
        assertEquals(1, engine.getBombCount(), "炸弹计数应为 1");
        assertEquals(2, engine.getMultiplier(), "一个炸弹翻一倍");
        assertEquals(6, engine.getScorePerFarmer(), "3 分 × 2 倍");

        // 之后地主每次出单张、农民过牌，直到地主出完 —— 春天再翻一倍
        int steps = 0;
        while (engine.getPhase() == DDZGamePhase.PLAYING) {
            assertTrue(++steps < MAX_STEPS);
            if (engine.getCurrentPlayerIndex() == landlord) {
                assertTrue(engine.playCards(landlord, List.of(engine.getPlayerHand(landlord).get(0))));
            } else {
                assertTrue(engine.pass(engine.getCurrentPlayerIndex()));
            }
        }
        assertTrue(engine.isSpring());
        assertEquals(4, engine.getMultiplier(), "炸弹 ×2 再叠春天 ×2");
        assertEquals(12, engine.getScorePerFarmer(), "3 分 × 4 倍");
    }

    /** 找出该座位手里的一个炸弹（同点数四张）；没有则返回 null。 */
    private static List<Integer> findBomb(DDZEngine engine, int seat) {
        var byRank = new java.util.HashMap<Integer, List<Integer>>();
        for (int c : engine.getPlayerHand(seat)) {
            if (c >= 52) continue;   // 王不构成普通炸弹
            byRank.computeIfAbsent(DDZEngine.getRank(c), k -> new java.util.ArrayList<>()).add(c);
        }
        for (var entry : byRank.entrySet()) {
            if (entry.getValue().size() == 4) return entry.getValue();
        }
        return null;
    }
}
