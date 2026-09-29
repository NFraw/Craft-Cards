package com.jokernan.craftycards.client;

/**
 * 客户端收到一份快照后该怎么处置（纯逻辑，可单测）。
 *
 * <p>存在的理由：这里曾漏掉"旁观者"这一档，导致旁观别人的牌局打完时，客户端照样弹了全屏结算遮罩，
 * 而点击关闭的分支又被 {@code myIndex < 0} 挡在前面——遮罩再也关不掉；结算后服务端已把这张桌
 * 从管理器移除，客户端不会再收到任何快照，于是一直卡在结算界面上。
 * 把"什么身份收到什么状态该做什么"收成一个可穷举的函数，避免再漏。
 */
final class ClientSnapshotPolicy {
    /** 收到快照后的处置方式。 */
    enum Action {
        /** 正常缓存并渲染（牌局进行中，或参局者自己的结算）。 */
        APPLY,
        /** 牌局已解散：提示一句然后清空状态。 */
        NOTIFY_AND_CLEAR,
        /** 旁观者看到别人的牌局结束：只提示结果，不弹全屏结算遮罩。 */
        NOTIFY_RESULT_AND_CLEAR
    }

    private ClientSnapshotPolicy() {}

    /**
     * @param sessionClosed        快照是否标记牌局已解散
     * @param settled              快照阶段是否为结算
     * @param viewerIsParticipant  接收者自己是否在牌局中（{@code myIndex >= 0}）
     */
    static Action decide(boolean sessionClosed, boolean settled, boolean viewerIsParticipant) {
        // 解散优先：无论身份，牌局都没了，提示一句即可
        if (sessionClosed) return Action.NOTIFY_AND_CLEAR;
        // 结算遮罩是给参局者的：旁观者看到的不是"他的牌局结束"，弹全屏会把世界挡住且无从关闭
        if (settled && !viewerIsParticipant) return Action.NOTIFY_RESULT_AND_CLEAR;
        return Action.APPLY;
    }
}
