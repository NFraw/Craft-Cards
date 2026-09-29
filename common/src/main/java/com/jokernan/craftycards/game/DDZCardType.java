package com.jokernan.craftycards.game;

/**
 * 斗地主牌型枚举。
 * <p>
 * 定义所有合法的出牌牌型，用于牌型判断和跟牌校验。
 * 牌型按优先级（权重）排序，权重越高越能压制其他牌型。
 * </p>
 *
 * <h3>牌型规则</h3>
 * <ul>
 *   <li>{@link #SINGLE} — 单张（任意一张牌）</li>
 *   <li>{@link #PAIR} — 对子（两张同点数牌）</li>
 *   <li>{@link #TRIPLE} — 三条（三张同点数牌）</li>
 *   <li>{@link #TRIPLE_ONE} — 三带一（三条 + 一张单牌）</li>
 *   <li>{@link #TRIPLE_PAIR} — 三带二（三条 + 一对）</li>
 *   <li>{@link #STRAIGHT} — 顺子（≥5 张连续单牌，不含 2 和王）</li>
 *   <li>{@link #PAIR_STRAIGHT} — 连对（≥3 对连续对子）</li>
 *   <li>{@link #PLANE} — 飞机（≥2 个连续三条，不带牌）</li>
 *   <li>{@link #PLANE_SINGLE} — 飞机带单（飞机 + 等量单牌）</li>
 *   <li>{@link #PLANE_PAIR} — 飞机带对（飞机 + 等量对子）</li>
 *   <li>{@link #FOUR_TWO} — 四带二单（四条 + 两张单牌）</li>
 *   <li>{@link #FOUR_TWO_PAIR} — 四带二对（四条 + 两对）</li>
 *   <li>{@link #BOMB} — 炸弹（四张同点数牌，可压制除火箭外的所有牌型）</li>
 *   <li>{@link #ROCKET} — 火箭（大小王，最大牌型，压制一切）</li>
 * </ul>
 *
 * @see DDZEngine#analyzeCardType
 */
public enum DDZCardType {
    /** 无效牌型（不能出牌）。 */
    INVALID,
    /** 单张。 */
    SINGLE,
    /** 对子（两张同点数）。 */
    PAIR,
    /** 三条（三张同点数）。 */
    TRIPLE,
    /** 三带一。 */
    TRIPLE_ONE,
    /** 三带二（三条 + 一对）。 */
    TRIPLE_PAIR,
    /** 顺子（≥5 张连续单牌）。 */
    STRAIGHT,
    /** 连对（≥3 对连续对子）。 */
    PAIR_STRAIGHT,
    /** 飞机（≥2 个连续三条）。 */
    PLANE,
    /** 飞机带单。 */
    PLANE_SINGLE,
    /** 飞机带对。 */
    PLANE_PAIR,
    /** 四带二单。 */
    FOUR_TWO,
    /** 四带二对。 */
    FOUR_TWO_PAIR,
    /** 炸弹（四张同点数）。 */
    BOMB,
    /** 火箭（大小王，最大牌型）。 */
    ROCKET;

    /**
     * 获取牌型的中文显示名 — 用于 HUD 提示（如"上轮出牌：三带一"）。
     * <p>枚举名是给代码/快照用的英文标识，直接展示给玩家可读性差，故单列一份显示名。</p>
     *
     * @return 牌型中文名
     */
    public String getDisplayName() {
        return switch (this) {
            case INVALID -> "无效";
            case SINGLE -> "单张";
            case PAIR -> "对子";
            case TRIPLE -> "三条";
            case TRIPLE_ONE -> "三带一";
            case TRIPLE_PAIR -> "三带二";
            case STRAIGHT -> "顺子";
            case PAIR_STRAIGHT -> "连对";
            case PLANE -> "飞机";
            case PLANE_SINGLE -> "飞机带单";
            case PLANE_PAIR -> "飞机带对";
            case FOUR_TWO -> "四带二";
            case FOUR_TWO_PAIR -> "四带二对";
            case BOMB -> "炸弹";
            case ROCKET -> "火箭";
        };
    }

    /**
     * 获取牌型权重 — 用于判断牌型间的压制关系。
     * <p>
     * 权重越高表示牌型越"强"：
     * 火箭(1000) > 炸弹(900) > 四带二对(12) > ... > 单张(1) > 无效(0)
     * </p>
     * <p>注意：实际跟牌校验在 {@link DDZEngine#playCards} 中通过牌型+点数判断，
     * 此权重仅用于辅助比较。</p>
     *
     * @return 牌型权重值
     */
    public int getWeight() {
        return switch (this) {
            case ROCKET -> 1000;
            case BOMB -> 900;
            case SINGLE -> 1;
            case PAIR -> 2;
            case TRIPLE -> 3;
            case TRIPLE_ONE -> 4;
            case TRIPLE_PAIR -> 5;
            case STRAIGHT -> 6;
            case PAIR_STRAIGHT -> 7;
            case PLANE -> 8;
            case PLANE_SINGLE -> 9;
            case PLANE_PAIR -> 10;
            case FOUR_TWO -> 11;
            case FOUR_TWO_PAIR -> 12;
            default -> 0;
        };
    }
}
