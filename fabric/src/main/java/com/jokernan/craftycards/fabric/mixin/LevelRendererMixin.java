package com.jokernan.craftycards.fabric.mixin;

import com.jokernan.craftycards.platform.WorldRenderHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渲染阶段接缝：在原版"半透明方块画完之后"那一点调 common 的渲染回调。
 *
 * <p><b>注入点不是猜的</b>：NeoForge 的 {@code RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS)}
 * 就是由它自己在原版 {@code LevelRenderer.renderSectionLayer(...)} 的末尾按渲染类型分发的
 * （NeoForge 源码里那行 {@code ClientHooks.dispatchRenderStage(...)}）。Fabric 没有等价的
 * 渲染阶段事件，所以在**同一个位置**注入、并按同一个条件过滤（只认 {@code RenderType.translucent()}），
 * 两边触发时机一致。</p>
 *
 * <p>为什么"每帧只触发一次"是成立的：{@code renderLevel} 里两处
 * {@code renderSectionLayer(RenderType.translucent(), ...)} 分别在
 * {@code if (this.transparencyChain != null)} 的两个互斥分支里，一帧只会走其中一支。</p>
 *
 * <p>原版没有 NeoForge 添加的 getFrustum()，因此用 {@code @Shadow} 读取当前/冻结视锥
 * 与渲染刻 {@code ticks}，复刻 NeoForge 事件的取值。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    /** 渲染刻（与 NeoForge 事件 {@code getRenderTick()} 同一个字段）。 */
    @Shadow
    private int ticks;

    /** 剔除用视锥（原版私有字段）。 */
    @Shadow
    private Frustum cullingFrustum;

    /** 冻结的视锥（调用 {@code captureFrustum()} 后非 null，原版私有字段）。 */
    @Shadow
    private Frustum capturedFrustum;

    /**
     * 当前生效的视锥。
     *
     * <p><b>为什么自己写这一句</b>：NeoForge 给 {@code LevelRenderer} 加了个公开的
     * {@code getFrustum()}（正是这两行的逻辑），原版没有——所以这里用 {@code @Shadow} 读私有字段
     * 复刻同一句，保证两边取到的是同一个视锥。</p>
     */
    private Frustum crafty_cards$frustum() {
        return this.capturedFrustum != null ? this.capturedFrustum : this.cullingFrustum;
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void crafty_cards$afterSectionLayer(RenderType renderType, double camX, double camY, double camZ,
                                                Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                                CallbackInfo ci) {
        if (renderType != RenderType.translucent()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WorldRenderHook.afterTranslucentBlocks(new WorldRenderHook.Context(
            mc.gameRenderer.getMainCamera(), crafty_cards$frustum(), this.ticks, mc.getTimer()));
    }
}
