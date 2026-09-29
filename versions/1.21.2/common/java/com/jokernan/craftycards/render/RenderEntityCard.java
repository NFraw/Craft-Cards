package com.jokernan.craftycards.render;

import com.jokernan.craftycards.entity.EntityCard;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.util.CardHelper;
import com.jokernan.craftycards.util.ItemHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemStack;

public class RenderEntityCard extends EntityRenderer<EntityCard, CardEntityRenderState> {
    public RenderEntityCard(EntityRendererProvider.Context context) { super(context); }
    @Override public CardEntityRenderState createRenderState() { return new CardEntityRenderState(); }
    @Override public void extractRenderState(EntityCard entity, CardEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.card = new ItemStack(entity.isCover() ? InitItems.CARD_COVERED.get() : InitItems.CARD.get());
        state.card.setDamageValue(entity.getTopStackID());
        var tag = ItemHelper.getOrCreateNBT(state.card);
        tag.putByte("SkinID", entity.getSkinID());
        ItemHelper.setNBT(state.card, tag);
        state.amount = entity.getStackAmount();
        state.rotation = entity.getRotation();
    }
    @Override public void render(CardEntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(state, pose, buffers, light);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-state.rotation + 180));
        pose.scale(1.5F, 1.5F, 1.5F);
        for (int i = 0; i < state.amount; i++)
            CardHelper.renderItem(state.card, Minecraft.getInstance().level, 0, i * 0.003D, 0, pose, buffers, light);
        pose.popPose();
    }
}
