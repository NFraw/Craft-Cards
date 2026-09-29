package com.jokernan.craftycards.game;

/**
 * 手牌可见性规则 —— 纯函数，可直接单测。
 *
 * <p>服务端在构建每个接收者的快照时用它决定 {@code visibleHands}：客户端只拿得到
 * 服务端下发的牌面数据，看不到的数据无从渲染，因此"能不能看牌"这件事完全由这里决定，
 * 不依赖客户端自觉（防作弊）。
 *
 * <p>规则矩阵：
 * <table border="1">
 *   <caption>谁能看到谁的手牌</caption>
 *   <tr><th>观察者</th><th>看自己</th><th>看他人</th></tr>
 *   <tr><td>对局参与者</td><td>否（自己的牌在 HUD，世界内不重复渲染）</td>
 *       <td>{@code participantsSeeFaces}（默认关）</td></tr>
 *   <tr><td>旁观者</td><td>无自己的牌</td><td>{@code spectatorsSeeCards}（默认开）</td></tr>
 * </table>
 */
public final class DDZVisibility {
    private DDZVisibility() {}

    /**
     * 某个非参局玩家该不该收到旁观快照（即能否看到这桌的状态与牌面）。
     *
     * <p>两条路径取并集：<b>主动观战</b>（右键开启，见 {@code DDZSession.handleWatch}）与
     * <b>走近自动共享</b>（可选，{@code ServerGameConfig.spectateRadius} &gt; 0 时才存在）。</p>
     *
     * <p>自动共享默认关闭是刻意的：只要走近就能看牌，等于把所有手牌摊在公共频道上，
     * 任何人在牌桌附近用改过的客户端都能看到——所以默认只能主动右键观战。</p>
     *
     * @param isWatcher     该玩家是否已通过右键开启观战
     * @param withinAutoRadius 是否落在自动共享半径内
     * @param autoShareEnabled 服务端是否启用了自动共享（半径 &gt; 0）
     */
    public static boolean spectatorReceives(boolean isWatcher, boolean withinAutoRadius,
                                           boolean autoShareEnabled) {
        return isWatcher || (autoShareEnabled && withinAutoRadius);
    }

    /**
     * 观察者能否看到某个座位上玩家的手牌<b>牌面</b>。
     *
     * @param viewerIsParticipant 观察者自己是否在牌局中
     * @param seatIsSelf          该座位是否就是观察者自己的座位
     * @param spectatorsSeeCards  服务端配置：旁观者能否看牌面
     * @param participantsSeeFaces 服务端配置：参与者能否看他人牌面
     */
    public static boolean canSeeHand(boolean viewerIsParticipant, boolean seatIsSelf,
                                     boolean spectatorsSeeCards, boolean participantsSeeFaces) {
        if (seatIsSelf) return false;
        return viewerIsParticipant ? participantsSeeFaces : spectatorsSeeCards;
    }
}
