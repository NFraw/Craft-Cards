package com.jokernan.craftycards.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CardHelper {
    public static final String[] CARD_SKIN_NAMES = {"card.skin.blue", "card.skin.red", "card.skin.black", "card.skin.pig"};

    public static void renderItem(ItemStack stack, Level level, double offsetX, double offsetY, double offsetZ,
                                  PoseStack poseStack, MultiBufferSource buffer, int combinedLight) {
        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = renderer.getModel(stack, level, null, 0);
        renderer.renderStatic(stack, ItemDisplayContext.GROUND, combinedLight, 0, poseStack, buffer, level, 0);
        poseStack.popPose();
    }

    public static MutableComponent getCardName(int id) {
        if (id == 52) return Component.translatable("card.joker_small");
        if (id == 53) return Component.translatable("card.joker_big");

        String type = "card.ace";
        int typeID = id / 4 + 1;

        if (typeID > 1 && typeID < 11) {
            type = "" + typeID;
        } else if (typeID == 11) {
            type = "card.jack";
        } else if (typeID == 12) {
            type = "card.queen";
        } else if (typeID == 13) {
            type = "card.king";
        }

        String suite = switch (id % 4) {
            case 1 -> "card.clubs";
            case 2 -> "card.diamonds";
            case 3 -> "card.hearts";
            default -> "card.spades";
        };

        return Component.translatable(type).append(" ")
                .append(Component.translatable("card.of").append(" "))
                .append(Component.translatable(suite));
    }

    public static boolean isJoker(int id) { return id >= 52; }
    public static boolean isSmallJoker(int id) { return id == 52; }
    public static boolean isBigJoker(int id) { return id == 53; }
}
