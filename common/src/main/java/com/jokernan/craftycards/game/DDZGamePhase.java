package com.jokernan.craftycards.game;

/**
 * 斗地主游戏阶段枚举。
 * <p>
 * 定义牌局的 4 个阶段，按顺序流转：
 * {@link #WAITING} → {@link #BIDDING} → {@link #PLAYING} → {@link #SETTLED}
 * </p>
 *
 * <h3>阶段说明</h3>
 * <ul>
 *   <li>{@link #WAITING} — 等待玩家加入，3 人坐满后自动进入叫分阶段</li>
 *   <li>{@link #BIDDING} — 叫地主阶段，3 人依次叫分（0-3），最高分者成为地主</li>
 *   <li>{@link #PLAYING} — 出牌阶段，地主先出，按顺时针轮流出牌/过牌</li>
 *   <li>{@link #SETTLED} — 结算阶段，一方出完所有牌，显示胜利界面</li>
 * </ul>
 *
 * @see DDZEngine#phase
 * @see DDZSession
 */
public enum DDZGamePhase {
    /** 等待玩家加入（尚未坐满 3 人）。 */
    WAITING,
    /** 叫地主阶段（轮流叫分，最高分者成为地主）。 */
    BIDDING,
    /** 出牌阶段（地主先出，顺时针轮流）。 */
    PLAYING,
    /** 结算阶段（一方出完所有牌，游戏结束）。 */
    SETTLED
}
