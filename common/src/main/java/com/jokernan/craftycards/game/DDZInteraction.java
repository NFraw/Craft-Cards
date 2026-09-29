package com.jokernan.craftycards.game;

/**
 * 牌桌交互的补充判定（纯逻辑，可单测）。
 *
 * <p>存在的理由是一个 Minecraft 的既有行为：右键方块时，
 * {@code flag1 = 潜行 && 手持物品} 为真则**跳过方块交互**、把右键让给物品
 * （这正是"能对着箱子潜行放方块"的原因）。于是"潜行 + 手持筹码物品右键牌桌"根本到不了方块——
 * 声明筹码失效；参局者手持扑克时 Shift+右键"离开牌局"同样失效。</p>
 *
 * <p>空手时不受影响：{@code ItemStack.doesSneakBypassUse} 对空手返回 true，
 * 所以"空手潜行右键 = 观战"一直是好的。</p>
 *
 * <p>修法是让牌桌在这种情形下强制拿到方块交互（{@code RightClickBlock} 事件里把
 * {@code useBlock} 置为 TRUE）。代价：无法再对着牌桌潜行放置方块——牌桌本就是可交互方块，
 * 这个取舍是可接受的。</p>
 */
public final class DDZInteraction {
    private DDZInteraction() {}

    /**
     * 是否应把右键强制交给方块（而不是让手持物品处理）。
     *
     * @param sneaking    玩家是否潜行
     * @param mainEmpty   主手是否为空
     * @param offhandEmpty 副手是否为空
     * @param isDdzTable  目标是斗地主牌桌
     */
    public static boolean shouldForceBlockUse(boolean sneaking, boolean mainEmpty,
                                             boolean offhandEmpty, boolean isDdzTable) {
        if (!isDdzTable) return false;
        if (!sneaking) return false;
        // 空手时 Minecraft 本来就把交互交给方块，无需干预；只有"潜行 + 手持物品"会被物品抢走
        return !(mainEmpty && offhandEmpty);
    }
}
