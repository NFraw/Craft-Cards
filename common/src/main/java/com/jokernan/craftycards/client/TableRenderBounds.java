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
 * <p>包围盒刻意取得比实际牌组大得多（半径 5 格，而座位在 3 格、牌组再外扩约 1 格），
 * 因此只会在牌桌确实完全离开视野时命中，不会出现边缘牌被提前裁掉的闪烁。
 */
final class TableRenderBounds {
    /** 以牌桌中心为准的水平半径（方块）。1×1 桌 + 桌面中央的出牌组，取 2 留足余量。 */
    private static final double RADIUS = 2.0;
    /** 以牌桌方块底面为准的上下余量（方块）。桌面在 y+0.875，立牌高约 1 格。 */
    private static final double HEIGHT = 1.5;

    private TableRenderBounds() {}

    /** 牌桌（含桌面中央的出牌）是否可能出现在当前视野内。 */
    static boolean visible(Frustum frustum, BlockPos tablePos) {
        double cx = tablePos.getX() + 0.5;
        double cy = tablePos.getY() + 0.5;
        double cz = tablePos.getZ() + 0.5;
        return frustum.isVisible(new AABB(
            cx - RADIUS, cy - HEIGHT, cz - RADIUS,
            cx + RADIUS, cy + HEIGHT, cz + RADIUS));
    }
}
