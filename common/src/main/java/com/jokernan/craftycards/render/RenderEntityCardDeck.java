package com.jokernan.craftycards.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jokernan.craftycards.entity.EntityCardDeck;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.util.CardHelper;
import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class RenderEntityCardDeck extends EntityRenderer<EntityCardDeck> {
    public RenderEntityCardDeck(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(EntityCardDeck pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        super.render(pEntity, pEntityYaw, pPartialTick, pPoseStack, pBuffer, pPackedLight);
        ItemStack cardStack = new ItemStack(InitItems.CARD_COVERED.get());
        CompoundTag nbt = ItemHelper.getOrCreateNBT(cardStack);
        nbt.putByte("SkinID", pEntity.getSkinID());
        ItemHelper.setNBT(cardStack, nbt);
        pPoseStack.pushPose();
        pPoseStack.mulPose(Axis.YP.rotationDegrees(-pEntity.getRotation() + 180));
        pPoseStack.scale(1.5F, 1.5F, 1.5F);
        for (byte i = 0; i < pEntity.getStackAmount() + 2; i++) {
            CardHelper.renderItem(cardStack, pEntity.level(), 0, i * 0.003D, 0, pPoseStack, pBuffer, pPackedLight);
        }
        pPoseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(EntityCardDeck pEntity) {
        return null;
    }
}
