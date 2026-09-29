package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手牌可见性规则测试。
 *
 * <p>这是防作弊的唯一闸门：客户端只能渲染服务端下发的牌面，故该矩阵一旦写错
 * 就等同于"要么看不了牌、要么把别人的牌泄给对手"，且很难在游戏内察觉，必须固定住。
 */
class DDZVisibilityTest {

    /** 默认配置（旁观者可见、参与者不可见）下的完整矩阵。 */
    @Test
    void defaultConfigurationMatrix() {
        boolean specs = true, parts = false;
        // 参与者：自己的看不到（HUD 已有），他人的看不到
        assertFalse(DDZVisibility.canSeeHand(true, true, specs, parts), "参与者不该在世界里再渲染自己的牌");
        assertFalse(DDZVisibility.canSeeHand(true, false, specs, parts), "默认配置下参与者不该看到他人牌面");
        // 旁观者：能看到所有人的牌面
        assertTrue(DDZVisibility.canSeeHand(false, false, specs, parts), "旁观者应能看到牌面");
        assertFalse(DDZVisibility.canSeeHand(false, true, specs, parts), "旁观者没有自己的座位");
    }

    /** 关掉旁观看牌：旁观者也只能看到牌背。 */
    @Test
    void spectatorsBlindWhenDisabled() {
        assertFalse(DDZVisibility.canSeeHand(false, false, false, false));
        assertFalse(DDZVisibility.canSeeHand(false, false, false, true));
    }

    /** 开放手牌（娱乐局）：参与者互相能看，但自己的牌仍不在世界里渲染。 */
    @Test
    void openHandModeForParticipants() {
        assertTrue(DDZVisibility.canSeeHand(true, false, true, true));
        assertFalse(DDZVisibility.canSeeHand(true, true, true, true), "即便开放手牌，自己的牌也不在世界里重复渲染");
    }

    /**
     * 旁观快照的接收规则：主动观战永远有效；"走近自动共享"只在服务端启用时有效。
     *
     * <p>守的是一条安全默认值：自动共享关着时，光走近（不右键）不该收到任何东西——
     * 否则任何人在牌桌附近用改过的客户端就能看到三家手牌。</p>
     */
    @Test
    void spectatorReceivesOnlyWhenOptedInOrAutoShareEnabled() {
        // 默认：自动共享关闭 → 只有右键观战者收得到
        assertTrue(DDZVisibility.spectatorReceives(true, true, false), "观战者应收到（哪怕不在半径内）");
        assertTrue(DDZVisibility.spectatorReceives(true, false, false), "观战者走远也应收到");
        assertFalse(DDZVisibility.spectatorReceives(false, true, false),
            "自动共享关闭时，光走近不该收到牌面数据（安全默认值）");
        assertFalse(DDZVisibility.spectatorReceives(false, false, false));

        // 服务端显式开启自动共享 → 走近即可
        assertTrue(DDZVisibility.spectatorReceives(false, true, true), "开启后走近应收到");
        assertFalse(DDZVisibility.spectatorReceives(false, false, true), "开启后也得在半径内");
    }

    /** 非参与者只看旁观开关、参与者只看参与者开关，两开关互不串味。 */
    @Test
    void switchesAreIndependent() {
        // 旁观开关开着、参与者开关关着：参与者仍看不到
        assertFalse(DDZVisibility.canSeeHand(true, false, true, false));
        // 旁观开关关着、参与者开关开着：旁观者仍看不到
        assertFalse(DDZVisibility.canSeeHand(false, false, false, true));
    }
}
