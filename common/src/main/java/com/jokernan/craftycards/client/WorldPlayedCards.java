package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.DDZGeom;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 世界内出牌渲染：玩家出牌后，牌**平铺**在桌面上，整组呈扇形摊开。
 * 复用 WorldHandCards 的渲染管线，时机是 {@code WorldRenderHook} 的"半透明方块之后"。
 * 所有可调参数来自 {@link RenderConfig} 的 {@code played*} 字段，可用 {@code /craftycards reload} 热更新。
 *
 * <p>每张牌的变换（JOML 右乘，顶点先吃后乘）：
 * <ul>
 *   <li><b>扇形</b>：先绕整组中心竖直轴旋转（{@code mulPose(Y(yaw))}）再沿横向平移 ——
 *       牌落在水平面的弧线上摊开，中间牌正对 +z，两端牌向两侧转开（"横着错开"）。
 *       若交换顺序（先平移后旋转）则是每张牌绕自己轴原地自转，不会摊开。</li>
 *   <li><b>平铺</b>：不做立牌旋转，牌面（模型 up 面）朝上，并抵消模型自身的中心偏移，
 *       使牌底正好落在桌面高度（{@link #TABLE_SURFACE_HEIGHT}）之上。</li>
 *   <li><b>朝向</b>：再绕竖直轴转 {@code faceYaw}，让牌面顶部指向出牌者（同真人打牌"牌朝自己"）。</li>
 *   <li>牌组整体偏向出牌玩家座位方向（{@link RenderConfig#playedOffset}）。</li>
 * </ul>
 */
public class WorldPlayedCards {
    private static final WorldPlayedCards INSTANCE = new WorldPlayedCards();

    /** 牌桌方块顶面高度（方块基座上方）：桌面模型占 y 13..16，故顶面在 +1.0。 */
    private static final float TABLE_SURFACE_HEIGHT = 16.0F / 16.0F;

    private WorldPlayedCards() {}

    public static WorldPlayedCards getInstance() {
        return INSTANCE;
    }

    public void render(WorldRenderHook.Context ctx) {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        // 出牌是公开信息（桌面上人人可见），旁观者（myIndex < 0）同样渲染
        if (snap == null) return;
        if (snap.phase() != DDZGamePhase.PLAYING && snap.phase() != DDZGamePhase.BIDDING) return;
        if (snap.lastPlayedCards().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = snap.table().pos();
        // 视锥体粗剔除：背对牌桌时整段跳过（与立牌渲染同一判定，保证两者要么都画要么都不画）
        if (!TableRenderBounds.visible(ctx.frustum(), pos)) return;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + TABLE_SURFACE_HEIGHT;
        double cz = pos.getZ() + 0.5;

        // 牌落在出牌那一刻定格的方位上：朝向角由服务端在出牌瞬间算好并随快照下发
        // （见 DDZSession.lastPlayedYaw）。早先按出牌者实时位置算，玩家一走动牌就绕桌转圈，
        // 站在桌心时还会突然翻转——玩家能自由走动后就不该这么算了。
        // 这里把 yaw 还原成水平方向，用于"偏向出牌者那侧"的微小位移与牌面朝向
        double yawRad = Math.toRadians(snap.lastPlayedYaw());
        double fx = Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        cx += fx * RenderConfig.playedOffset;
        cz += fz * RenderConfig.playedOffset;
        Vec3 cam = ctx.camera().getPosition();

        PoseStack pose = new PoseStack();
        pose.translate(cx - cam.x, cy - cam.y, cz - cam.z);

        // 渲染出牌
        List<Integer> cards = snap.lastPlayedCards();
        int n = cards.size();
        if (n == 0) return;

        // 计算牌的起始位置（居中排列）
        float totalWidth = (n - 1) * RenderConfig.playedCardGap;
        float startX = -totalWidth / 2;

        // 牌面朝向出牌者（真人打牌的"牌朝自己摆"）：贴图 v=0（牌面顶部）在模型 -z 端，
        // 故让 +z 指向出牌者当时所在的方向，他看到的牌就是正立的
        float faceYaw = snap.lastPlayedYaw();

        // 循环不变量提到循环外：光照值查询与渲染器/缓冲源每帧相同，避免逐张重复获取
        int light = LevelRenderer.getLightColor(mc.level, pos);
        ItemRenderer itemRenderer = mc.getItemRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // 不试图关闭深度测试：ItemRenderer 用的实体类 RenderType 在 CompositeState 里
        // 默认带 LEQUAL_DEPTH_TEST，flush 时 setupRenderState 会把深度测试重新打开，
        // 外面的 disableDepthTest 会被覆盖。所以牌按正常深度关系绘制，靠"不共面"避免闪动。
        try {
            for (int i = 0; i < n; i++) {
                pose.pushPose();

                // 归一化横向位置 t ∈ [-1, 1]（0 为中间牌）
                float t = n > 1 ? (i - (n - 1) / 2F) / ((n - 1) / 2F) : 0F;
                // 扇形：每张牌绕整组中心竖直轴旋转（中间 0°，两端 ±playedMaxYaw）——平铺后即桌面上的摊开
                float yaw = t * RenderConfig.playedMaxYaw;
                // 沿横排的原始位置
                float x = startX + i * RenderConfig.playedCardGap;

                // 逐张抬高：牌高只有 0.0078 方块且横向大幅重叠，若全摊在同一高度就会共面
                // z-fighting（表现为随视角闪动）；叠起来既消除共面，也让薄牌看起来有厚度。
                // 再整体抬高 playedSurfaceLift，避免最下面那张与桌面顶面共面
                float stackY = RenderConfig.playedSurfaceLift + i * RenderConfig.playedStackStep;

                // 先旋转后平移：牌绕整组中心竖直轴转 yaw 后，落在水平面的弧线上摊开
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.translate(x, stackY, 0);
                // 平铺：不做立牌旋转，牌面（模型 up 面）朝上；再转 faceYaw 让牌面正对出牌者。
                // ItemRenderer 已把模型居中（内部 translate(-0.5)），烘焙后牌底面正好在模型 y=0，
                // 故无需再补偿——牌自然平贴在 pose 原点所在的桌面高度上
                pose.mulPose(Axis.YP.rotationDegrees(faceYaw));
                pose.scale(RenderConfig.playedCardScale, RenderConfig.playedCardScale, RenderConfig.playedCardScale);

                itemRenderer.renderStatic(
                    CardStacks.face(cards.get(i)),
                    ItemDisplayContext.NONE,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffers,
                    mc.level,
                    0
                );
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            // 无需还原深度测试状态：本方法从不改动它（原因见上方说明），
            // 早先版本在这里 disable/enable 深度测试属于无效代码
        }
    }
}
