package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DDZEngineTest {

    // Card encoding: 0-51 = standard 52 cards, 52 = small joker, 53 = big joker
    // Rank = cardId / 4 + 1 (1=A, 2-10, 11=J, 12=Q, 13=K), Ace mapped to 14
    // Suit = cardId % 4 (0=♠,1=♣,2=♦,3=♥)
    // So: 0,1,2,3 = Aces; 4,5,6,7 = 2s; 8,9,10,11 = 3s; etc.

    @Test
    void testShuffleAndDeal() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();

        assertEquals(DDZGamePhase.BIDDING, engine.getPhase());
        assertEquals(17, engine.getPlayerHand(0).size());
        assertEquals(17, engine.getPlayerHand(1).size());
        assertEquals(17, engine.getPlayerHand(2).size());
        assertEquals(3, engine.getDipai().size());
    }

    @Test
    void testBidToStart() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();

        int first = engine.getCurrentPlayerIndex();
        engine.bid(first, 3);

        assertEquals(DDZGamePhase.PLAYING, engine.getPhase());
        assertEquals(first, engine.getLandlordIndex());
        assertEquals(20, engine.getPlayerHand(first).size());
    }

    @Test
    void testSingleCard() {
        assertEquals(DDZCardType.SINGLE, DDZEngine.analyzeCardType(List.of(0)));
    }

    @Test
    void testPair() {
        // Two Aces: cardId 0 and 1 are both rank A
        assertEquals(DDZCardType.PAIR, DDZEngine.analyzeCardType(List.of(0, 1)));
    }

    @Test
    void testTriple() {
        // Three Aces: cardId 0, 1, 2
        assertEquals(DDZCardType.TRIPLE, DDZEngine.analyzeCardType(List.of(0, 1, 2)));
    }

    @Test
    void testTripleWithOne() {
        // Three Aces (0,1,2) + one 2 (cardId 4)
        assertEquals(DDZCardType.TRIPLE_ONE, DDZEngine.analyzeCardType(List.of(0, 1, 2, 4)));
    }

    @Test
    void testTripleWithPair() {
        // Three Aces (0,1,2) + pair of 2s (4,5)
        assertEquals(DDZCardType.TRIPLE_PAIR, DDZEngine.analyzeCardType(List.of(0, 1, 2, 4, 5)));
    }

    @Test
    void testStraight() {
        // 3-4-5-6-7: one of each rank
        // rank 3 = cardId 8, rank 4 = cardId 12, rank 5 = cardId 16, rank 6 = cardId 20, rank 7 = cardId 24
        assertEquals(DDZCardType.STRAIGHT, DDZEngine.analyzeCardType(List.of(8, 12, 16, 20, 24)));
    }

    @Test
    void testStraightInvalid() {
        // Only 4 cards - not enough for straight
        assertNotEquals(DDZCardType.STRAIGHT, DDZEngine.analyzeCardType(List.of(8, 12, 16, 20)));
    }

    @Test
    void testBomb() {
        // 4 Aces: cardId 0,1,2,3
        assertEquals(DDZCardType.BOMB, DDZEngine.analyzeCardType(List.of(0, 1, 2, 3)));
    }

    @Test
    void testRocket() {
        // Small joker (52) + Big joker (53)
        assertEquals(DDZCardType.ROCKET, DDZEngine.analyzeCardType(List.of(52, 53)));
    }

    @Test
    void testJokerRank() {
        assertEquals(16, DDZEngine.getRank(52)); // Small joker
        assertEquals(17, DDZEngine.getRank(53)); // Big joker
    }

    @Test
    void testAceRank() {
        assertEquals(14, DDZEngine.getRank(0)); // Ace of spades
        assertEquals(14, DDZEngine.getRank(1)); // Ace of clubs
    }

    @Test
    void testNumberRank() {
        assertEquals(3, DDZEngine.getRank(8));  // 3 of spades
        assertEquals(10, DDZEngine.getRank(36)); // 10 of spades
        assertEquals(15, DDZEngine.getRank(4)); // 2 of spades (仅次于王)
    }

    @Test
    void testFaceCardRank() {
        assertEquals(11, DDZEngine.getRank(40)); // Jack
        assertEquals(12, DDZEngine.getRank(44)); // Queen
        assertEquals(13, DDZEngine.getRank(48)); // King
    }

    @Test
    void testInvalidCardType() {
        // 2 random cards that aren't a pair - Ace + 2
        assertEquals(DDZCardType.INVALID, DDZEngine.analyzeCardType(List.of(0, 4)));
    }

    @Test
    void testPlayCards() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();

        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);

        List<Integer> hand = engine.getPlayerHand(landlord);
        List<Integer> card = List.of(hand.get(0));
        assertTrue(engine.playCards(landlord, card));
        assertEquals(19, engine.getPlayerHand(landlord).size());
    }

    @Test
    void testPassWhenNotYourTurn() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();

        int first = engine.getCurrentPlayerIndex();
        engine.bid(first, 3);

        int wrongPlayer = (first + 1) % 3;
        assertFalse(engine.pass(wrongPlayer));
    }

    @Test
    void testCardTypeWeight() {
        assertTrue(DDZCardType.ROCKET.getWeight() > DDZCardType.BOMB.getWeight());
        assertTrue(DDZCardType.BOMB.getWeight() > DDZCardType.STRAIGHT.getWeight());
    }

    @Test
    void testPairStraight() {
        // 3-3-4-4-5-5: two of each rank
        // rank 3 = 8,9; rank 4 = 12,13; rank 5 = 16,17
        assertEquals(DDZCardType.PAIR_STRAIGHT, DDZEngine.analyzeCardType(List.of(8, 9, 12, 13, 16, 17)));
    }

    @Test
    void testGetWinnerBeforeEnd() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        assertEquals(-1, engine.getWinnerTeam());
    }

    @Test
    void testStraightCannotContain2() {
        // 2-3-4-5-6: 2 is rank 15, should not be allowed in straight
        assertEquals(DDZCardType.INVALID, DDZEngine.analyzeCardType(List.of(4, 8, 12, 16, 20)));
    }

    @Test
    void testStraightA2345Invalid() {
        // A-2-3-4-5: 2 is rank 15, should not be allowed
        assertEquals(DDZCardType.INVALID, DDZEngine.analyzeCardType(List.of(0, 4, 8, 12, 16)));
    }

    @Test
    void testPlayDuplicateCardRejected() {
        // 每张牌 ID 唯一，手中只有一张 x，却声明 [x,x,x] 三张 —— 必须拒绝（防作弊）
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);

        int card = engine.getPlayerHand(landlord).get(0);
        assertFalse(engine.playCards(landlord, List.of(card, card, card)));
        assertEquals(20, engine.getPlayerHand(landlord).size());
    }

    @Test
    void testTripleOneBeatsByTripleNotKicker() {
        // 555带9 之后出 666带3：比较三条 rank（6 > 5），带牌 9 > 3 不应影响
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int next = (landlord + 1) % 3;

        setHand(engine, landlord, new ArrayList<>(List.of(16, 17, 18, 36, 44))); // 5♠5♣5♦ + 9♠ + 备用
        setHand(engine, next, new ArrayList<>(List.of(20, 21, 22, 8, 0)));      // 6♠6♣6♦ + 3♠ + 备用

        assertTrue(engine.playCards(landlord, List.of(16, 17, 18, 36)));
        assertTrue(engine.playCards(next, List.of(20, 21, 22, 8)));
    }

    @Test
    void testFourTwoBeatsByFourNotKicker() {
        // 6666带2 之后出 7777带3：比较四条 rank（7 > 6），带牌 2 是大点不应让 6666 更"大"
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int next = (landlord + 1) % 3;

        setHand(engine, landlord, new ArrayList<>(List.of(20, 21, 22, 23, 4, 48, 44))); // 6666 + 2♠ + K♠ + 备用
        setHand(engine, next, new ArrayList<>(List.of(24, 25, 26, 27, 8, 49, 0)));     // 7777 + 3♠ + K♣ + 备用

        assertTrue(engine.playCards(landlord, List.of(20, 21, 22, 23, 4, 48)));
        assertTrue(engine.playCards(next, List.of(24, 25, 26, 27, 8, 49)));
    }

    @Test
    void testPlaneSingleBeatsByHighestTriple() {
        // 4455... 先手 444555 带 22（带牌 2 是大点），后手 666777 带 33（带牌 3 是小点）
        // 应按最高三条比较（7 > 5）：带牌 2 > 3 不应让先手的牌"更大"
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int next = (landlord + 1) % 3;

        // 先手：4♠4♣4♦(12,13,14) 5♠5♣5♦(16,17,18) + 2♠2♣(4,5)
        setHand(engine, landlord, new ArrayList<>(List.of(12, 13, 14, 16, 17, 18, 4, 5, 44)));
        // 后手：6♠6♣6♦(20,21,22) 7♠7♣7♦(24,25,26) + 3♠3♣(8,9)
        setHand(engine, next, new ArrayList<>(List.of(20, 21, 22, 24, 25, 26, 8, 9, 0)));

        assertTrue(engine.playCards(landlord, List.of(12, 13, 14, 16, 17, 18, 4, 5)));
        assertTrue(engine.playCards(next, List.of(20, 21, 22, 24, 25, 26, 8, 9)));
    }

    @Test
    void testPlanePairAndSingle() {
        // 333 444 + 55 + 66 = 飞机带对；333 444 + 55 = 飞机带单（带牌是拆开的一对，允许）
        assertEquals(DDZCardType.PLANE_PAIR,
            DDZEngine.analyzeCardType(List.of(8, 9, 10, 12, 13, 14, 16, 17, 20, 21)));
        assertEquals(DDZCardType.PLANE_SINGLE,
            DDZEngine.analyzeCardType(List.of(8, 9, 10, 12, 13, 14, 16, 17)));
        // 带牌可以是对 2（2 只在顺子/连对/飞机本体里被禁，作带牌不受限）
        assertEquals(DDZCardType.PLANE_PAIR,
            DDZEngine.analyzeCardType(List.of(8, 9, 10, 12, 13, 14, 4, 5, 20, 21)));
    }

    /**
     * 四张同点不能当"三条"用在飞机里——否则炸弹被拆着打。
     *
     * <p>回归：{@code 3333 444 55 6}（10 张）曾被判成飞机带对。成因是三点被算作三条后
     * 占掉了三条名额却仍贡献 4 张牌，带牌少一张也能凑够"点数个数"，于是 `55 + 6`
     * 这种"一对 + 一张单牌"混过了带对校验。修复后必须判无效。</p>
     */
    @Test
    void testBombCannotBeSplitIntoPlaneWings() {
        // 3333 444 + 55 + 6
        assertEquals(DDZCardType.INVALID,
            DDZEngine.analyzeCardType(List.of(8, 9, 10, 11, 12, 13, 14, 16, 17, 20)));
        // 三条段更长时同样不行：3333 444 555 + 66 77 + 8
        assertEquals(DDZCardType.INVALID, DDZEngine.analyzeCardType(
            List.of(8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 20, 21, 24, 25, 28)));
        // 四张只能落在带牌里也不行：444 555 + 3333（带牌张数对不上飞机带单）
        assertEquals(DDZCardType.INVALID,
            DDZEngine.analyzeCardType(List.of(12, 13, 14, 16, 17, 18, 8, 9, 10, 11)));
    }

    /** 上面那手非法牌不仅判无效，还必须真的打不出去（走 playCards 全路径，不只是分析函数）。 */
    @Test
    void testIllegalPlaneCannotBePlayed() {
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);

        List<Integer> illegal = List.of(8, 9, 10, 11, 12, 13, 14, 16, 17, 20);
        setHand(engine, landlord, new ArrayList<>(illegal));
        assertFalse(engine.playCards(landlord, illegal), "拆开炸弹凑成的飞机带对不该能出");
        assertEquals(10, engine.getPlayerHand(landlord).size(), "被拒的出牌不该动到手牌");
    }

    @Test
    void testPlayStraightAsLead() {        // 起手直接出顺子 3-4-5-6-7（第一手，无上家牌约束）
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);

        setHand(engine, landlord, new ArrayList<>(List.of(8, 12, 16, 20, 24, 0, 4, 28)));
        assertTrue(engine.playCards(landlord, List.of(8, 12, 16, 20, 24)));
        assertEquals(DDZCardType.STRAIGHT, engine.getLastPlayedType());
    }

    @Test
    void testStraightFollowsStraight() {
        // 3-4-5-6-7 之后出 4-5-6-7-8：同牌型同张数，按最高牌比较（8 > 7）应能压过
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int next = (landlord + 1) % 3;

        setHand(engine, landlord, new ArrayList<>(List.of(8, 12, 16, 20, 24, 0, 4)));
        setHand(engine, next, new ArrayList<>(List.of(12, 16, 20, 24, 28, 1, 5)));

        assertTrue(engine.playCards(landlord, List.of(8, 12, 16, 20, 24)));
        assertTrue(engine.playCards(next, List.of(12, 16, 20, 24, 28)));
    }

    @Test
    void testPlayPairStraightAsLead() {
        // 三连对 3-3-4-4-5-5 起手直接出
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);

        setHand(engine, landlord, new ArrayList<>(List.of(8, 9, 12, 13, 16, 17, 0, 4)));
        assertTrue(engine.playCards(landlord, List.of(8, 9, 12, 13, 16, 17)));
        assertEquals(DDZCardType.PAIR_STRAIGHT, engine.getLastPlayedType());
    }

    @Test
    void testPairStraightFollowsPairStraight() {
        // 33-44-55 之后出 44-55-66：最高对 6 > 5，应能压过
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int next = (landlord + 1) % 3;

        setHand(engine, landlord, new ArrayList<>(List.of(8, 9, 12, 13, 16, 17, 0)));
        setHand(engine, next, new ArrayList<>(List.of(12, 13, 16, 17, 20, 21, 1)));

        assertTrue(engine.playCards(landlord, List.of(8, 9, 12, 13, 16, 17)));
        assertTrue(engine.playCards(next, List.of(12, 13, 16, 17, 20, 21)));
    }

    @Test
    void testLeaderReLeadsAfterTwoPasses() {
        // 一方出牌，另外两位都不要（连续两次 pass）后，该玩家可重新出任意合适的牌型
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int a = landlord;
        int b = (landlord + 1) % 3;
        int c = (landlord + 2) % 3;

        setHand(engine, a, new ArrayList<>(List.of(8, 12, 16, 20, 24, 0, 4)));
        setHand(engine, b, new ArrayList<>(List.of(28, 29, 32, 33, 36, 1)));
        setHand(engine, c, new ArrayList<>(List.of(40, 41, 44, 45, 48, 2)));

        assertTrue(engine.playCards(a, List.of(8, 12, 16, 20, 24))); // a 出顺子
        assertTrue(engine.pass(b));                                   // b 不要
        assertTrue(engine.pass(c));                                   // c 不要 → 轮回 a，lastPlayedBy 清空
        assertEquals(a, engine.getCurrentPlayerIndex());
        // a 现在可以出任意牌型：出单张 0（A）应成功
        assertTrue(engine.playCards(a, List.of(0)));
    }

    @Test
    void testLeaderCannotPassAfterTwoPasses() {
        // 两连过回到自己后是新一轮牌权，必须出牌，不能继续 pass
        DDZEngine engine = new DDZEngine();
        engine.shuffleAndDeal();
        int landlord = engine.getCurrentPlayerIndex();
        engine.bid(landlord, 3);
        int a = landlord;
        int b = (landlord + 1) % 3;
        int c = (landlord + 2) % 3;

        setHand(engine, a, new ArrayList<>(List.of(8, 12, 16, 20, 24, 0)));
        setHand(engine, b, new ArrayList<>(List.of(28, 29, 32, 33, 36, 1)));
        setHand(engine, c, new ArrayList<>(List.of(40, 41, 44, 45, 48, 2)));

        assertTrue(engine.playCards(a, List.of(8, 12, 16, 20, 24)));
        assertTrue(engine.pass(b));
        assertTrue(engine.pass(c));
        assertEquals(a, engine.getCurrentPlayerIndex());
        assertFalse(engine.pass(a)); // 已清空 lastPlayedBy，不能再 pass
    }

    /** 用反射注入指定手牌，便于构造确定性的比较场景。 */
    private void setHand(DDZEngine engine, int index, List<Integer> cards) {
        try {
            Field f = DDZEngine.class.getDeclaredField("playerHands");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<List<Integer>> hands = (List<List<Integer>>) f.get(engine);
            hands.get(index).clear();
            hands.get(index).addAll(cards);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
