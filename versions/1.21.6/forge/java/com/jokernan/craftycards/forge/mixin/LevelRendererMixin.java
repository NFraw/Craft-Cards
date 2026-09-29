package com.jokernan.craftycards.forge.mixin;

import com.jokernan.craftycards.platform.WorldRenderHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run inside the main frame-graph pass, immediately after translucent terrain. */
@Mixin(ChunkSectionsToRender.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void crafty_cards$afterTranslucent(ChunkSectionLayerGroup group, CallbackInfo ci) {
        if (group != ChunkSectionLayerGroup.TRANSLUCENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WorldRenderHook.afterTranslucentBlocks(new WorldRenderHook.Context(
            mc.gameRenderer.getMainCamera(), mc.levelRenderer.getCapturedFrustum(),
            mc.levelRenderer.getTicks(), mc.getDeltaTracker()));
    }
}