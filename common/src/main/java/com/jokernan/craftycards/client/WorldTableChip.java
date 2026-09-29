package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 桌面悬浮的"本局筹码"图标：在牌桌正上方缓缓自转地显示本局用的是哪种筹码物品。
 *
 * <p>为什么要有它：筹码类型由玩家声明，所有人（含旁观者）都需要一眼看出本局用什么下注。
 * 用**物品模型**而不是文字来显示，是因为"这是钻石还是铁锭"本身就是物品形状的信息，
 * 而且可以直接复用已有的世界内物品渲染管线（与 {@link WorldPlayedCards} 同一条）。</p>
 *
 * <p>数据来源是快照里已有的 {@code betItem}（物品注册名），故无需为显示改动网络协议。</p>
 */
public class WorldTableChip {
    private static final WorldTableChip INSTANCE = new WorldTableChip();

    /** 牌桌方块顶面高度（方块基座上方）：桌面模型占 y 13..16，故顶面在 +1.0。 */
    private static final double TABLE_SURFACE_HEIGHT = 16.0 / 16.0;

    /** 缓存避免每帧构造 ItemStack（筹码只在声明/更换时变，故按 id 缓存一份即可）。 */
    private String cachedId = "";
    private ItemStack cachedStack = ItemStack.EMPTY;

    private WorldTableChip() {}

    public static WorldTableChip getInstance() {
        return INSTANCE;
    }

    public void render(WorldRenderHook.Context ctx) {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap == null || snap.sessionClosed()) return;
        // 结算后牌局已结束，筹码没有意义，收掉
        if (snap.phase() == DDZGamePhase.SETTLED) return;
        // 还没配筹码，或本桌压根不玩筹码（chipRequired = 0：全局闸门关掉、或这张桌自己关了）：
        // 后者即使桌上配着筹码类型也不该在桌面飘一个图标——那会让人以为这桌要押东西
        if (snap.betItem().isEmpty() || snap.chipRequired() <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = snap.table().pos();
        Vec3 cam = ctx.camera().getPosition();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + TABLE_SURFACE_HEIGHT + RenderConfig.chipDisplayHeight
            + bob(ctx);
        double cz = pos.getZ() + 0.5;

        // 视锥体剔除 + 距离上限：为远处根本看不清的桌白做模型渲染
        double dist = cam.distanceToSqr(cx, cy, cz);
        double maxDist = RenderConfig.chipDisplayMaxDist;
        if (dist > maxDist * maxDist) return;
        if (!ctx.frustum().isVisible(new AABB(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5))) {
            return;
        }

        ItemStack chip = stackFor(snap.betItem());
        if (chip.isEmpty()) return;

        float scale = RenderConfig.chipDisplayScale;
        int light = LevelRenderer.getLightColor(mc.level, pos);
        ItemRenderer itemRenderer = mc.getItemRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        PoseStack pose = new PoseStack();
        pose.translate(cx - cam.x, cy - cam.y, cz - cam.z);
        // 绕竖直轴缓慢自转：让所有人从任何角度都能看清是什么物品
        pose.mulPose(Axis.YP.rotationDegrees(spinAngle(ctx)));
        pose.scale(scale, scale, scale);
        // GROUND 是"物品掉在地上"那套显示变换，配合自转就是经典的悬浮物品观感
        itemRenderer.renderStatic(chip, ItemDisplayContext.GROUND, light,
            OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        buffers.endBatch();
    }

    /** 自转角度（度）：按渲染刻累计，逐帧连续，不受快照刷新频率影响。 */
    private static float spinAngle(WorldRenderHook.Context ctx) {
        float ticks = ctx.renderTick() + ctx.partialTick().getGameTimeDeltaPartialTick(false);
        return ticks * RenderConfig.chipSpinDegPerTick;
    }

    /** 上下轻微浮动，让图标更像"悬浮"而不是钉在空中。 */
    private static double bob(WorldRenderHook.Context ctx) {
        float ticks = ctx.renderTick() + ctx.partialTick().getGameTimeDeltaPartialTick(false);
        return Math.sin(ticks / 14.0) * RenderConfig.chipBobAmplitude;
    }

    /** 把物品注册名变成可渲染的 ItemStack（按 id 缓存，避免逐帧构造）。 */
    private ItemStack stackFor(String itemId) {
        if (itemId.equals(cachedId)) return cachedStack;
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        // 未注册的物品 id（例如服务端配了客户端没有的模组物品）会回退成空气，此时不渲染
        ItemStack stack = item == net.minecraft.world.item.Items.AIR
            ? ItemStack.EMPTY
            : new ItemStack(item);
        cachedId = itemId;
        cachedStack = stack;
        return stack;
    }
}
