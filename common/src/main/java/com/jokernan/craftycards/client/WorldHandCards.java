package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.DDZGeom;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 世界内持牌渲染：每个参与牌局的玩家"手里"立着一排牌，跟随该玩家的位置与视线。
 * 不渲染本地玩家自己的（HUD 底部已有自己的手牌）。
 *
 * <p>牌组放在持牌人前方 {@link RenderConfig#handForwardDist} 格、胸口高度
 * {@link RenderConfig#handHeight}，牌面朝向持牌人（真人持牌朝自己）：
 * <ul>
 *   <li>持牌人看到自己的牌面；站在他对面的人看到牌背。</li>
 *   <li>旁观者走到持牌人身后"看他的牌"就能看到牌面——前提是服务端下发了牌面数据
 *       （{@code GameStatePayload.visibleHands}，按"谁能看谁的牌"的服务端配置逐接收者构建：
 *       参与者的客户端默认拿不到他人牌面，想作弊也无从看起）。</li>
 * </ul>
 *
 * <p>牌面朝向：牌组在玩家前方 forward×dist 处，"牌 → 玩家"方向 = −forward，
 * 用 {@link DDZGeom#facingYaw} 把牌的正面（局部 +z）转回持牌人。
 *
 * <p>高度与遮挡是两件事，互不干扰：
 * <ul>
 *   <li><b>高度</b>：拱形——中间牌最高、两端回到基准高度（{@link RenderConfig#handArchHeight}）</li>
 *   <li><b>遮挡</b>：逐张沿<b>本张牌自己的法线</b>前移，{@link RenderConfig#handStackStep}
 *       是相邻两片的平面距离，于是从持牌人这一侧看，编号大的（右）压住左边那张，
 *       露出每张牌的左上角；前移量按"平面距离恒定"反解（{@link DDZGeom#fanDepthAdvance}），
 *       左右两支露出的宽度一致</li>
 * </ul>
 * 遮挡只由深度决定，所以拱形怎么起伏都不影响"左边那张被右边压住"。
 * 从背面看时左右与远近会一起镜像，遮挡线索两侧天然自洽，无需特殊处理。
 *
 * <p>一个固有取舍：拱形让牌堆右半边"靠中间那张"的顶边更高，若把这个高度差读成堆叠顺序，
 * 会看成"向中心堆叠"。不想要这层干扰时把 {@link RenderConfig#handArchHeight} 设为 0 即可
 * （那时只剩深度台阶一个线索；台阶本身是等平面距离的，不会再左右偏斜）。
 *
 * <p>三个玩家共用同一套公式，没有按玩家或观察者区分的分支。
 *
 * <p>所有可调参数来自 {@link RenderConfig} 的 {@code hand*} 字段，可用 {@code /craftycards reload} 热更新。
 */
public class WorldHandCards {
    private static final WorldHandCards INSTANCE = new WorldHandCards();

    private WorldHandCards() {}

    public static WorldHandCards getInstance() {
        return INSTANCE;
    }

    public void render(WorldRenderHook.Context ctx) {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap == null) return;
        if (snap.phase() != DDZGamePhase.BIDDING && snap.phase() != DDZGamePhase.PLAYING) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 cam = ctx.camera().getPosition();
        PoseStack pose = new PoseStack();

        // 不试图关闭深度测试：ItemRenderer 用的实体类 RenderType 在 CompositeState 里默认带
        // LEQUAL_DEPTH_TEST，flush 时 setupRenderState 会把深度测试重新打开，外面的
        // disableDepthTest 会被覆盖（实测确认）。所以立牌按正常深度关系绘制：
        // 正面/侧面看得到，站到持牌人正后方时会被他的身体挡住——要读牌就往侧面站一点。
        // 循环不变量提到循环外：渲染器、缓冲源、牌背 ItemStack 每帧都相同
        ItemRenderer itemRenderer = mc.getItemRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ItemStack cardBack = CardStacks.covered();
        try {
            for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
                if (i == snap.myIndex()) continue; // 自己的手牌在 HUD，世界内不重复渲染
                int count = snap.playerCardCounts().get(i);
                if (count <= 0) continue;
                // 服务端按接收者权限下发的牌面；空 = 只能看到牌背（数量 = 剩余张数）
                List<Integer> visible = i < snap.visibleHands().size() ? snap.visibleHands().get(i) : List.of();
                boolean showFaces = !visible.isEmpty();
                String name = i < snap.playerNames().size() ? snap.playerNames().get(i) : "";
                AbstractClientPlayer holder = findPlayer(mc.level, name);
                if (holder == null) continue; // 玩家实体不在跟踪范围（太远），画不了

                // 视锥体剔除 + 距离上限：按持牌人包围盒（含前方牌组余量）判断是否可能可见。
                // 距离上限避免为远处根本看不清的玩家白做模型渲染
                if (cam.distanceToSqr(holder.getX(), holder.getY(), holder.getZ())
                    > (double) RenderConfig.handMaxRenderDist * RenderConfig.handMaxRenderDist) continue;
                AABB box = holder.getBoundingBox().inflate(2.0);
                if (!ctx.frustum().isVisible(box)) continue;

                float yaw = holder.getYHeadRot();
                double fx = DDZGeom.forwardX(yaw);
                double fz = DDZGeom.forwardZ(yaw);
                double px = holder.getX() + fx * RenderConfig.handForwardDist;
                double py = holder.getY() + RenderConfig.handHeight;
                double pz = holder.getZ() + fz * RenderConfig.handForwardDist;
                // 牌面朝向持牌人：牌的正面（局部 +z）指向 "牌 → 玩家" = −forward
                float faceYaw = DDZGeom.facingYaw(-fx, -fz);

                int light = LevelRenderer.getLightColor(mc.level, holder.blockPosition());

                // 牌组以视线前方为中心：centered 为第 j 张牌相对组中心的位置，中间牌为 0
                float half = (count - 1) / 2F;

                // 逐张前移量：让每对相邻牌的**平面距离**恒为 planeDist（见 DDZGeom.fanDepthAdvance）。
                // 等量且方向固定的前移，投影到每张牌自己的法线上是 gap·sinθ + step·cosθ——左半边
                // （θ < 0）两项相抵、右半边（θ > 0）两项相加，于是同样的横向间隔下左右观感不一致，
                // 整叠牌还朝一侧斜出去。这里把那个 θ 项反解掉。
                // 下限取牌模型厚度：牌心间距小于厚度时，重叠区两张牌的实体就会互相穿插
                float planeDist = Math.max(RenderConfig.handStackStep, cardSlabThickness() * 1.25F);
                // 深度曲线先算一遍求中点再整体居中，使牌组仍落在 handForwardDist 附近
                // （否则整叠牌会朝持牌人一侧斜出去）
                float zAcc = 0F, zMin = 0F, zMax = 0F;
                for (int j = 1; j < count; j++) {
                    zAcc += DDZGeom.fanDepthAdvance(RenderConfig.handCardGap, planeDist,
                        (j - 1 - half) * RenderConfig.handFanAngle);
                    zMin = Math.min(zMin, zAcc);
                    zMax = Math.max(zMax, zAcc);
                }
                float z = -(zMin + zMax) * 0.5F;

                pose.pushPose();
                pose.translate(px - cam.x, py - cam.y, pz - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(faceYaw));

                for (int j = 0; j < count; j++) {
                    pose.pushPose();
                    float centered = j - half;
                    // 第 j 张相对第 j-1 张的前移量（平面距离恒定；右半边可能为负 = 略往后退）
                    if (j > 0) {
                        z += DDZGeom.fanDepthAdvance(RenderConfig.handCardGap, planeDist,
                            (j - 1 - half) * RenderConfig.handFanAngle);
                    }

                    // === 步骤1: 拱形高度 + 立牌高度补偿 + 水平定位 ===
                    // 整体高度效果：中间牌最高、两端回到基准高度（t=0 为中间，|t|=1 为两端）。
                    // 这是纯视觉的高度起伏，不参与遮挡判定——谁盖住谁完全由下面的深度台阶
                    // （handStackStep）决定，所以拱形不会改变"左被右压住"的关系。
                    // 注意一个固有的观感取舍：拱形会让牌堆右半边"靠中间那张"的顶边更高，
                    // 若把这个高度差读成堆叠顺序，就会看成"向中心堆叠"；不想要这层干扰可以把
                    // handArchHeight 设为 0（只剩深度台阶这一个线索）。
                    float tt = half > 0F ? centered / half : 0F;
                    float archY = half > 0F ? (1F - tt * tt) * RenderConfig.handArchHeight : 0F;
                    // ItemRenderer 渲染前会把烘焙模型居中（内部无条件 translate(-0.5)），故立牌以**牌心**
                    // 对齐 pose 原点：先把牌心抬高半个牌高（模型长边烘焙后为 1.0 格），再叠加拱形高度。
                    // 抬升是纯竖直平移，与后面的绕竖直轴旋转可交换，写在旋转之前即最后作用于顶点。
                    pose.translate(0, RenderConfig.handCardScale * 0.5F + archY, 0);
                    // 先定位后旋转（平移右乘靠左、后作用于顶点），使旋转绕单张牌自身中心而非整组中心
                    pose.translate(centered * RenderConfig.handCardGap, 0, 0);
                    // 逐张沿本张牌的法线前移：本帧 +z 指向持牌人，也就是看向牌面的那一侧。
                    // 前移量按"平面距离恒定"给出，所以每对相邻牌露出的一样多；越靠右的牌越贴近
                    // 观看者、盖住左边那张，露出每张牌的左上角（点数/花色所在）——这正是真人把牌
                    // 摊开给人看的样子，旁观者一眼能读全。用 j 递增（而不是 centered）保证
                    // "右压左"与牌堆朝向无关
                    pose.translate(0, 0, z);

                    // === 步骤3: 旋转（JOML 右乘，顶点先吃后乘，以下按"最先作用于顶点"的倒序书写）===
                    // 3a. 左右倾斜（Z 轴）：长边向侧面倾倒，正值右侧下沉
                    pose.mulPose(Axis.ZP.rotationDegrees(RenderConfig.handTiltZ));
                    // 3b. 前后倾倒（X 轴）：牌面朝上下翻转，正值向玩家方向倾
                    pose.mulPose(Axis.XP.rotationDegrees(RenderConfig.handTiltX));
                    // 3c. 绕单张牌竖直中心（Y 轴）扇形：中间牌 0° 正对持牌人，两端对称外翻
                    pose.mulPose(Axis.YP.rotationDegrees(centered * RenderConfig.handFanAngle));
                    // 3d. 立牌：up 面（+y）转到 +z，贴图正立（最先作用于顶点的旋转）
                    pose.mulPose(Axis.XP.rotationDegrees(90));

                    // === 步骤4: 缩放并渲染 ===
                    pose.scale(RenderConfig.handCardScale, RenderConfig.handCardScale, RenderConfig.handCardScale);
                    // j < visible.size() 是防御性判断：两者都来自同一份服务端手牌、正常必然一致，
                    // 但渲染线程越界会直接崩游戏，宁可多这一层也不冒风险
                    ItemStack stack = showFaces && j < visible.size() ? CardStacks.face(visible.get(j)) : cardBack;
                    itemRenderer.renderStatic(stack,
                        ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, mc.level, 0);
                    pose.popPose();
                }
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            // 无需还原深度测试状态：本方法从不改动它（原因见上方说明）
        }
    }

    /**
     * 牌模型的厚度（方块）：{@code card_base.json} 的中心块厚 0.5 模型单位 = 0.5/16 格，
     * 随 {@link RenderConfig#handCardScale} 一起缩放。牌心间距小于它，重叠区的实体就会互相穿插。
     */
    private static float cardSlabThickness() {
        return 0.5F / 16F * RenderConfig.handCardScale;
    }

    /** 按玩家名查找客户端玩家实体；找不到（未加载/太远/名字空）返回 null。 */
    private static AbstractClientPlayer findPlayer(ClientLevel level, String name) {
        if (name == null || name.isEmpty()) return null;
        for (AbstractClientPlayer p : level.players()) {
            if (p.getGameProfile().getName().equals(name)) return p;
        }
        return null;
    }
}
