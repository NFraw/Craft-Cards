package com.jokernan.craftycards.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 桌面上出牌渲染的视锥体可见性判定。
 *
 * <p>两个渲染器都挂在 {@code RenderLevelStageEvent} 上，只要玩家在牌局里就每帧执行——哪怕
 * 扭头背对牌桌，最多 40 张牌的模型渲染与缓冲提交仍会照做一遍，纯粹浪费。这里用与实体渲染
 * 相同的视锥体做一次粗剔除：不在视野内就整段跳过。
 *
 * <p>包围盒覆盖整张矩形牌桌并留出牌组余量，避免大桌边缘被提前裁掉。
 */
final class TableRenderBounds {
    /** 整张牌桌边界之外的水平余量（方块）。 */
    private static final double RADIUS = 2.0;
    /** 桌心上下的剔除余量（方块），桌面位于底座 y+1。 */
    private static final double HEIGHT = 1.5;

    private TableRenderBounds() {}

    /** 牌桌（含桌面中央的出牌）是否可能出现在当前视野内。 */
    static boolean visible(Frustum frustum, BlockPos tablePos, int width, int depth) {
        double cx = tablePos.getX() + width / 2.0;
        double cy = tablePos.getY() + 0.5;
        double cz = tablePos.getZ() + depth / 2.0;
        return frustum.isVisible(new AABB(
            cx - width / 2.0 - RADIUS, cy - HEIGHT, cz - depth / 2.0 - RADIUS,
            cx + width / 2.0 + RADIUS, cy + HEIGHT, cz + depth / 2.0 + RADIUS));
    }
}
