package com.jokernan.craftycards.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jokernan.craftycards.entity.EntityCard;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.util.CardHelper;
import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class RenderEntityCard extends EntityRenderer<EntityCard> {
    public RenderEntityCard(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(EntityCard pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        super.render(pEntity, pEntityYaw, pPartialTick, pPoseStack, pBuffer, pPackedLight);
        ItemStack card = new ItemStack(InitItems.CARD.get());
        card.setDamageValue(pEntity.getTopStackID());
        if (pEntity.isCover()) {
            card = new ItemStack(InitItems.CARD_COVERED.get());
            CompoundTag nbt = ItemHelper.getOrCreateNBT(card);
            nbt.putByte("SkinID", pEntity.getSkinID());
            ItemHelper.setNBT(card, nbt);
        }
        pPoseStack.pushPose();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(-pEntity.getRotation() + 180));
        pPoseStack.scale(1.5F, 1.5F, 1.5F);
        for (byte i = 0; i < pEntity.getStackAmount(); i++) {
            CardHelper.renderItem(card, pEntity.level(), 0, i * 0.003D, 0, pPoseStack, pBuffer, pPackedLight);
        }
        pPoseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(EntityCard pEntity) {
        return null;
    }
}
