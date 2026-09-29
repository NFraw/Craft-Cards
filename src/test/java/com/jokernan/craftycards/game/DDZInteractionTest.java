package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 牌桌交互补充判定的测试。
 *
 * <p>守的是一条容易反复踩的坑：Minecraft 在"潜行 + 手持物品"时会**跳过方块交互**、
 * 把右键让给物品。牌桌必须在这种情形下强制拿到交互，否则声明筹码与 Shift+右键离开都失效；
 * 但同时又**不能影响其他方块**，否则会破坏原版"潜行可以对着方块放东西"的行为。</p>
 */
class DDZInteractionTest {

    /** 正是需要修的那个情形：牌桌 + 潜行 + 手持物品。 */
    @Test
    void tableWithSneakAndItemIsForced() {
        assertTrue(DDZInteraction.shouldForceBlockUse(true, false, true, true),
            "主手有物品、副手空 → 应强制交给方块");
        assertTrue(DDZInteraction.shouldForceBlockUse(true, true, false, true),
            "副手有物品同样会抢走交互 → 也应强制");
        assertTrue(DDZInteraction.shouldForceBlockUse(true, false, false, true));
    }

    /** 空手潜行本来就能触发方块（doesSneakBypassUse 对空手返回 true），无需干预。 */
    @Test
    void emptyHandNeedsNoForcing() {
        assertFalse(DDZInteraction.shouldForceBlockUse(true, true, true, true),
            "空手潜行右键方块本来就会触发，不该被改写");
    }

    /** 不潜行时走的是正常交互，不该干预。 */
    @Test
    void normalRightClickUntouched() {
        assertFalse(DDZInteraction.shouldForceBlockUse(false, false, false, true));
        assertFalse(DDZInteraction.shouldForceBlockUse(false, false, true, true));
    }

    /** 关键：绝不能影响其他方块——否则潜行对着箱子/地面放方块的行为就被破坏了。 */
    @Test
    void otherBlocksAreNeverAffected() {
        for (boolean mainEmpty : new boolean[]{true, false}) {
            for (boolean offEmpty : new boolean[]{true, false}) {
                assertFalse(DDZInteraction.shouldForceBlockUse(true, mainEmpty, offEmpty, false),
                    "非牌桌方块不该被强制（main=" + mainEmpty + " off=" + offEmpty + "）");
            }
        }
    }
}
