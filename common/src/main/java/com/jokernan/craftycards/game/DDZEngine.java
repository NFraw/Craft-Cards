package com.jokernan.craftycards.game;

import java.util.*;

/**
 * 斗地主引擎 - 纯 Java 逻辑，不依赖 Minecraft 环境。
 * 卡牌编码: 0-51 = 标准 52 张牌, 52 = 小王, 53 = 大王
 * 花色: id % 4 (0=♠,1=♣,2=♦,3=♥)
 * 牌面值: id / 4 + 1 (1=A, 2-10, 11=J, 12=Q, 13=K)
 * 大小王: 52=小王, 53=大王
 */
public class DDZEngine {
    public static final int PLAYER_COUNT = 3;
    public static final int CARDS_PER_PLAYER = 17;
    public static final int DIPI_COUNT = 3;

    private DDZGamePhase phase = DDZGamePhase.WAITING;
    private final List<List<Integer>> playerHands = new ArrayList<>();
    private final List<Integer> dipai = new ArrayList<>();
    private int landlordIndex = -1;
    private int currentPlayerIndex = -1;
    private int lastPlayedBy = -1;
    private List<Integer> lastPlayedCards = null;
    private DDZCardType lastPlayedType = DDZCardType.INVALID;
    private int lastPlayedRank = -1;
    private int passCount = 0;
    private int bidScore = 0;
    private int bidderIndex = -1;
    private int bidRound = 0;

    // === 计分（市面常见规则：底分 × 倍数） ===

    /** 炸弹与火箭出现的次数——每出现一次，倍数翻一倍。 */
    private int bombCount;
    /** 地主出牌的手数（用于判定反春天：地主只出了开局那一手就被农民赢走）。 */
    private int landlordPlayCount;
    /** 各座位是否出过牌（用于判定春天：地主赢且两农民一张都没出过）。 */
    private final boolean[] hasPlayed = new boolean[PLAYER_COUNT];

    public DDZEngine() {
        for (int i = 0; i < PLAYER_COUNT; i++) {
            playerHands.add(new ArrayList<>());
        }
    }

    // === 发牌 ===

    public void shuffleAndDeal() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 54; i++) deck.add(i);
        Collections.shuffle(deck);

        for (int i = 0; i < PLAYER_COUNT; i++) playerHands.get(i).clear();
        dipai.clear();

        for (int i = 0; i < CARDS_PER_PLAYER * PLAYER_COUNT; i++) {
            playerHands.get(i % PLAYER_COUNT).add(deck.get(i));
        }
        for (int i = CARDS_PER_PLAYER * PLAYER_COUNT; i < 54; i++) {
            dipai.add(deck.get(i));
        }

        for (var hand : playerHands) Collections.sort(hand);
        phase = DDZGamePhase.BIDDING;
        bidderIndex = new Random().nextInt(PLAYER_COUNT);
        currentPlayerIndex = bidderIndex;
        bidRound = 0;
        bidScore = 0;
    }

    // === 叫地主 ===

    public boolean bid(int playerIndex, int score) {
        if (phase != DDZGamePhase.BIDDING || playerIndex != currentPlayerIndex) return false;
        if (score < 0 || score > 3) return false;
        if (score > 0 && score <= bidScore) return false;

        if (score > 0) {
            bidScore = score;
            landlordIndex = playerIndex;
        }

        bidRound++;

        if (score == 3) {
            startPlaying();
            return true;
        }

        if (bidRound >= PLAYER_COUNT) {
            if (landlordIndex < 0) {
                shuffleAndDeal();
                return true;
            }
            startPlaying();
            return true;
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % PLAYER_COUNT;
        return true;
    }

    private void startPlaying() {
        playerHands.get(landlordIndex).addAll(dipai);
        Collections.sort(playerHands.get(landlordIndex));
        phase = DDZGamePhase.PLAYING;
        bombCount = 0;
        landlordPlayCount = 0;
        Arrays.fill(hasPlayed, false);
        currentPlayerIndex = landlordIndex;
        lastPlayedBy = -1;
        lastPlayedCards = null;
        lastPlayedType = DDZCardType.INVALID;
        lastPlayedRank = -1;
        passCount = 0;
    }

    // === 出牌 ===

    public boolean playCards(int playerIndex, List<Integer> cards) {
        if (phase != DDZGamePhase.PLAYING || playerIndex != currentPlayerIndex) return false;
        if (cards == null || cards.isEmpty()) return false;

        List<Integer> hand = playerHands.get(playerIndex);
        // 每张牌 ID 唯一（0-53 各一张）：逐张从手牌副本移除一次，
        // 拒绝重复声明同一张牌（如 [x,x,x]），防作弊。
        List<Integer> remaining = new ArrayList<>(hand);
        for (int id : cards) {
            if (!remaining.remove(Integer.valueOf(id))) return false;
        }

        DDZCardType type = analyzeCardType(cards);
        if (type == DDZCardType.INVALID) return false;

        int rank = getMainRank(cards, type);

        if (lastPlayedBy >= 0 && lastPlayedBy != playerIndex) {
            if (type == DDZCardType.ROCKET) {
                // 火箭最大
            } else if (lastPlayedType == DDZCardType.ROCKET) {
                return false;
            } else if (type == DDZCardType.BOMB && lastPlayedType != DDZCardType.BOMB) {
                // 炸弹压非炸弹
            } else if (type == DDZCardType.BOMB && lastPlayedType == DDZCardType.BOMB) {
                if (rank <= lastPlayedRank) return false;
            } else if (type != lastPlayedType || cards.size() != lastPlayedCards.size()) {
                return false;
            } else {
                if (rank <= lastPlayedRank) return false;
            }
        }

        hand.clear();
        hand.addAll(remaining);
        lastPlayedBy = playerIndex;
        lastPlayedCards = new ArrayList<>(cards);
        lastPlayedType = type;
        lastPlayedRank = rank;
        passCount = 0;

        // 计分统计：炸弹/火箭各翻一倍；记录出过牌的座位与地主手数，供春天判定
        hasPlayed[playerIndex] = true;
        if (playerIndex == landlordIndex) landlordPlayCount++;
        if (type == DDZCardType.BOMB || type == DDZCardType.ROCKET) bombCount++;

        if (hand.isEmpty()) {
            phase = DDZGamePhase.SETTLED;
            return true;
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % PLAYER_COUNT;
        return true;
    }

    public boolean pass(int playerIndex) {
        if (phase != DDZGamePhase.PLAYING || playerIndex != currentPlayerIndex) return false;
        if (lastPlayedBy < 0 || lastPlayedBy == playerIndex) return false;

        passCount++;
        currentPlayerIndex = (currentPlayerIndex + 1) % PLAYER_COUNT;

        if (passCount >= PLAYER_COUNT - 1) {
            lastPlayedBy = -1;
            lastPlayedCards = null;
            lastPlayedType = DDZCardType.INVALID;
            lastPlayedRank = -1;
            passCount = 0;
        }
        return true;
    }

    // === 牌型分析 ===

    public static DDZCardType analyzeCardType(List<Integer> cards) {
        if (cards == null || cards.isEmpty()) return DDZCardType.INVALID;

        int n = cards.size();
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (int id : cards) {
            int rank = getRank(id);
            rankCount.merge(rank, 1, Integer::sum);
        }

        // 火箭
        if (n == 2 && cards.contains(52) && cards.contains(53)) return DDZCardType.ROCKET;

        // 炸弹
        if (n == 4 && rankCount.size() == 1) return DDZCardType.BOMB;

        // 单张
        if (n == 1) return DDZCardType.SINGLE;

        // 对子
        if (n == 2 && rankCount.size() == 1 && rankCount.values().iterator().next() == 2) return DDZCardType.PAIR;

        // 三条
        if (n == 3 && rankCount.size() == 1 && rankCount.values().iterator().next() == 3) return DDZCardType.TRIPLE;

        // 三带一
        if (n == 4 && rankCount.size() == 2) {
            for (int count : rankCount.values()) {
                if (count == 3) return DDZCardType.TRIPLE_ONE;
            }
        }

        // 三带二
        if (n == 5 && rankCount.size() == 2) {
            boolean hasThree = false, hasTwo = false;
            for (int count : rankCount.values()) {
                if (count == 3) hasThree = true;
                if (count == 2) hasTwo = true;
            }
            if (hasThree && hasTwo) return DDZCardType.TRIPLE_PAIR;
        }

        // 顺子 (≥5 张连续单牌，不含 2 和王)
        if (n >= 5) {
            List<Integer> ranks = new ArrayList<>(rankCount.keySet());
            Collections.sort(ranks);
            if (rankCount.size() == n && isStraight(ranks) && ranks.get(ranks.size() - 1) <= 14) {
                return DDZCardType.STRAIGHT;
            }
        }

        // 连对 (≥3 对连续对子)
        if (n >= 6 && n % 2 == 0) {
            boolean allPairs = true;
            for (int count : rankCount.values()) {
                if (count != 2) { allPairs = false; break; }
            }
            if (allPairs) {
                List<Integer> ranks = new ArrayList<>(rankCount.keySet());
                Collections.sort(ranks);
                if (isStraight(ranks) && ranks.get(ranks.size() - 1) <= 14) {
                    return DDZCardType.PAIR_STRAIGHT;
                }
            }
        }

        // 飞机 (≥2 个连续三条)
        List<Integer> triples = new ArrayList<>();
        List<Integer> others = new ArrayList<>();
        for (var entry : rankCount.entrySet()) {
            if (entry.getValue() >= 3) triples.add(entry.getKey());
            else for (int i = 0; i < entry.getValue(); i++) others.add(entry.getKey());
        }
        Collections.sort(triples);

        if (triples.size() >= 2 && isStraight(triples) && triples.get(triples.size() - 1) <= 14) {
            int tripleCount = triples.size();
            if (n == tripleCount * 3) return DDZCardType.PLANE;
            if (n == tripleCount * 4 && others.size() == tripleCount) return DDZCardType.PLANE_SINGLE;
            if (n == tripleCount * 5) {
                Map<Integer, Integer> otherCount = new HashMap<>();
                for (int o : others) otherCount.merge(o, 1, Integer::sum);
                // 带牌必须是 tripleCount 个对子：点数个数一致，**且**每个点恰好 2 张。
                // 只比点数个数不够——四张同点会以"count >= 3"进入 triples（占掉三条名额却不减少
                // 自己的张数），剩下的带牌少一张也能凑够点数个数，于是 3333 444 55 6 这种
                // "炸弹拆开 + 一张单牌凑数"的手牌会被判成飞机带对，绕过炸弹不能拆着打的限制。
                boolean allPairs = otherCount.size() == tripleCount;
                for (int count : otherCount.values()) if (count != 2) allPairs = false;
                if (allPairs) return DDZCardType.PLANE_PAIR;
            }
        }

        // 四带二(单)
        if (n == 6) {
            for (int count : rankCount.values()) {
                if (count == 4) return DDZCardType.FOUR_TWO;
            }
        }

        // 四带二(对)
        if (n == 8) {
            boolean hasFour = false;
            int pairCount = 0;
            for (int count : rankCount.values()) {
                if (count == 4) hasFour = true;
                if (count == 2) pairCount++;
            }
            if (hasFour && pairCount == 2) return DDZCardType.FOUR_TWO_PAIR;
        }

        return DDZCardType.INVALID;
    }

    private static boolean isStraight(List<Integer> sortedRanks) {
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i) - sortedRanks.get(i - 1) != 1) return false;
        }
        return true;
    }

    public static int getRank(int cardId) {
        if (cardId == 52) return 16; // 小王
        if (cardId == 53) return 17; // 大王
        int rank = cardId / 4 + 1;
        if (rank == 1) return 14; // A = 14
        if (rank == 2) return 15; // 2 = 15 (仅次于王)
        return rank;
    }

    private static int getMainRank(List<Integer> cards, DDZCardType type) {
        Map<Integer, Integer> rankCount = new HashMap<>();
        for (int id : cards) rankCount.merge(getRank(id), 1, Integer::sum);

        return switch (type) {
            case SINGLE -> rankCount.keySet().iterator().next();
            case PAIR -> rankCount.entrySet().stream()
                .filter(e -> e.getValue() == 2).mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
            // 三条/三带一/三带二：主 rank 是三条的 rank，带牌不参与比较
            case TRIPLE, TRIPLE_ONE, TRIPLE_PAIR -> rankCount.entrySet().stream()
                .filter(e -> e.getValue() == 3).mapToInt(Map.Entry::getKey).max().orElse(-1);
            case STRAIGHT, PAIR_STRAIGHT -> rankCount.keySet().stream()
                .mapToInt(Integer::intValue).max().orElse(-1);
            // 炸弹/四带二：主 rank 是四条，带牌不参与比较
            case BOMB, FOUR_TWO, FOUR_TWO_PAIR -> rankCount.entrySet().stream()
                .filter(e -> e.getValue() == 4).mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
            // 飞机（含带单/带对）：主 rank 是最高连续三条
            case PLANE, PLANE_SINGLE, PLANE_PAIR -> rankCount.entrySet().stream()
                .filter(e -> e.getValue() >= 3).mapToInt(Map.Entry::getKey).max().orElse(-1);
            case ROCKET -> 17;
            default -> rankCount.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        };
    }

    // === Getters ===

    public DDZGamePhase getPhase() { return phase; }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public int getLandlordIndex() { return landlordIndex; }
    public int getBidScore() { return bidScore; }
    public List<Integer> getDipai() { return Collections.unmodifiableList(dipai); }
    public List<Integer> getPlayerHand(int index) { return Collections.unmodifiableList(playerHands.get(index)); }
    public int getLastPlayedBy() { return lastPlayedBy; }
    public List<Integer> getLastPlayedCards() { return lastPlayedCards == null ? null : Collections.unmodifiableList(lastPlayedCards); }
    public DDZCardType getLastPlayedType() { return lastPlayedType; }
    public boolean isLandlord(int playerIndex) { return playerIndex == landlordIndex; }

    // === 计分（底分 × 倍数） ===

    /** 底分 = 叫分（1/2/3）。 */
    public int getBaseScore() {
        return bidScore;
    }

    /** 炸弹 + 火箭出现的次数。 */
    public int getBombCount() {
        return bombCount;
    }

    /**
     * 春天：地主获胜，且两个农民<b>一张牌都没出过</b>。
     * <p>市面规则里春天翻一倍。</p>
     */
    public boolean isSpring() {
        if (phase != DDZGamePhase.SETTLED || landlordIndex < 0) return false;
        if (!playerHands.get(landlordIndex).isEmpty()) return false;   // 地主没赢
        for (int i = 0; i < PLAYER_COUNT; i++) {
            if (i != landlordIndex && hasPlayed[i]) return false;
        }
        return true;
    }

    /**
     * 反春天（夏天）：农民获胜，且地主<b>只出了开局那一手牌</b>。
     * <p>市面规则里反春天同样翻一倍。</p>
     */
    public boolean isAntiSpring() {
        if (phase != DDZGamePhase.SETTLED || landlordIndex < 0) return false;
        if (playerHands.get(landlordIndex).isEmpty()) return false;    // 地主赢了，不是反春天
        return landlordPlayCount <= 1;
    }

    /**
     * 本局倍数：{@code 2^(炸弹+火箭数) × 春天系数}。
     *
     * <p>与市面常见规则一致：每个炸弹或火箭翻一倍（即 ×2）；春天或反春天各再翻一倍
     * （两者互斥：一个要求地主赢、另一个要求农民赢，不可能同时成立）。</p>
     */
    public int getMultiplier() {
        int multiplier = 1 << Math.min(bombCount, 20);   // 上限防溢出：20 个炸弹已远超实际牌局
        if (isSpring() || isAntiSpring()) multiplier *= 2;
        return multiplier;
    }

    /** 单个农民本局的得失分（正值表示赢得）：{@code 底分 × 倍数}。地主得失为其两倍。 */
    public int getScorePerFarmer() {
        return getBaseScore() * getMultiplier();
    }

    public int getWinnerTeam() {
        if (phase != DDZGamePhase.SETTLED) return -1;
        if (playerHands.get(landlordIndex).isEmpty()) return landlordIndex;
        return -2; // 农民赢
    }
}
