package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 快照处置决策的穷举测试。
 *
 * <p>这里守的是一个真实事故：旁观别人牌局打完时，客户端弹了全屏结算遮罩，
 * 而关闭遮罩的分支被 {@code myIndex < 0} 挡着走不到，结算后服务端又不再下发快照，
 * 于是遮罩永久卡在屏幕上。三条规则穷举如下，少任何一条都会复现该事故。</p>
 */
class ClientSnapshotPolicyTest {

    /** 牌局进行中的普通快照：谁都要正常渲染。 */
    @Test
    void normalSnapshotApplies() {
        assertEquals(ClientSnapshotPolicy.Action.APPLY,
            ClientSnapshotPolicy.decide(false, false, true));
        assertEquals(ClientSnapshotPolicy.Action.APPLY,
            ClientSnapshotPolicy.decide(false, false, false));
    }

    /** 参局者的结算：弹结算遮罩，由他点击关闭。 */
    @Test
    void participantSettlementShowsOverlay() {
        assertEquals(ClientSnapshotPolicy.Action.APPLY,
            ClientSnapshotPolicy.decide(false, true, true));
    }

    /** 旁观者拿到的结算：绝不能进结算遮罩，只能提示一句。 */
    @Test
    void spectatorNeverGetsSettlementOverlay() {
        assertEquals(ClientSnapshotPolicy.Action.NOTIFY_RESULT_AND_CLEAR,
            ClientSnapshotPolicy.decide(false, true, false));
    }

    /** 牌局解散：不分身份，都是提示 + 清空。 */
    @Test
    void closedSessionAlwaysNotifiesAndClears() {
        for (boolean settled : new boolean[]{false, true}) {
            for (boolean participant : new boolean[]{false, true}) {
                assertEquals(ClientSnapshotPolicy.Action.NOTIFY_AND_CLEAR,
                    ClientSnapshotPolicy.decide(true, settled, participant),
                    "解散应优先于结算：settled=" + settled + " participant=" + participant);
            }
        }
    }
}
