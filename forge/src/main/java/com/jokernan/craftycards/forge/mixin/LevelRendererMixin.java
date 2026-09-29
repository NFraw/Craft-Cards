package com.jokernan.craftycards.forge.mixin;

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

/** 在半透明方块绘制完成后调用公共世界渲染器，与 Fabric 使用相同的原版注入点。 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private int ticks;

    @Shadow
    private Frustum cullingFrustum;

    @Shadow
    private Frustum capturedFrustum;

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



