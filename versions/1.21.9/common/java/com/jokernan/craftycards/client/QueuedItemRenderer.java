package com.jokernan.craftycards.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Bridge for world cards after Minecraft moved item drawing to submission queues. */
public final class QueuedItemRenderer {
    private QueuedItemRenderer() {}

    public static void renderStatic(ItemStack stack, ItemDisplayContext context, int light, int overlay,
                                    PoseStack pose, MultiBufferSource buffers, Level level, int seed) {
        submit(stack, context, light, overlay, pose,
            Minecraft.getInstance().gameRenderer.getSubmitNodeStorage(), level, seed);
    }

    public static void submit(ItemStack stack, ItemDisplayContext context, int light, int overlay,
                               PoseStack pose, SubmitNodeCollector collector, Level level, int seed) {
        var state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        state.submit(pose, collector, light, overlay, 0);
    }

    public static void flush() {
        Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
    }
}
