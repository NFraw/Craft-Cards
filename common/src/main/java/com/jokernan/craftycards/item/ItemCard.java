package com.jokernan.craftycards.item;

import com.jokernan.craftycards.util.CardHelper;
import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ItemCard extends ItemCardCovered {
    public ItemCard() {}

    @Override
    public void appendHoverText(ItemStack pStack, TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        pTooltipComponents.add(CardHelper.getCardName(pStack.getDamageValue()).withStyle(ChatFormatting.GOLD));
        super.appendHoverText(pStack, pContext, pTooltipComponents, pIsAdvanced);
    }
}
