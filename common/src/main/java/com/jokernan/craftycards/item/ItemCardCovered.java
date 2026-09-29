package com.jokernan.craftycards.item;

import com.jokernan.craftycards.entity.EntityCard;
import com.jokernan.craftycards.entity.EntityCardDeck;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.item.base.ItemBase;
import com.jokernan.craftycards.util.CardHelper;
import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class ItemCardCovered extends ItemBase {
    public ItemCardCovered() {
        // durability(54) 使 damage 组件可存 0..53（牌面 ID），否则 setDamageValue 被 clamp 到 0，所有牌显示成 A
        super(new Properties().stacksTo(1).durability(54));
    }

    @Override
    public boolean isBarVisible(ItemStack pStack) {
        return false; // 牌不是工具，隐藏耐久条
    }

    @Override
    public void appendHoverText(ItemStack pStack, TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        CompoundTag nbt = ItemHelper.getNBT(pStack);
        byte skinID = nbt.getByte("SkinID");
        pTooltipComponents.add(Component.translatable("lore.cover").append(" ").withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(CardHelper.CARD_SKIN_NAMES[skinID]).withStyle(ChatFormatting.AQUA)));
    }

    public void flipCard(ItemStack heldItem, Player player, InteractionHand hand) {
        if (heldItem.getItem() instanceof ItemCardCovered) {
            CompoundTag heldNBT = ItemHelper.getNBT(heldItem);
            Item nextCard = InitItems.CARD.get();
            if (!heldNBT.getBoolean("Covered")) nextCard = InitItems.CARD_COVERED.get();
            ItemStack newCard = new ItemStack(nextCard);
            newCard.setDamageValue(heldItem.getDamageValue());
            CompoundTag newNBT = ItemHelper.getOrCreateNBT(newCard);
            if (heldNBT.hasUUID("UUID")) newNBT.putUUID("UUID", heldNBT.getUUID("UUID"));
            newNBT.putByte("SkinID", heldNBT.getByte("SkinID"));
            newNBT.putBoolean("Covered", !heldNBT.getBoolean("Covered"));
            ItemHelper.setNBT(newCard, newNBT);
            player.setItemInHand(hand, newCard);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack stack = pPlayer.getItemInHand(pUsedHand);
        flipCard(stack, pPlayer, pUsedHand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        if (pLevel.getGameTime() % 60 == 0 && pEntity instanceof Player player) {
            BlockPos pos = player.blockPosition();
            CompoundTag nbt = ItemHelper.getNBT(pStack);
            if (nbt.hasUUID("UUID")) {
                UUID id = nbt.getUUID("UUID");
                if (id.getLeastSignificantBits() == 0) return;
                List<EntityCardDeck> closeDecks = pLevel.getEntitiesOfClass(EntityCardDeck.class,
                    new AABB(pos.getX() - 20, pos.getY() - 20, pos.getZ() - 20, pos.getX() + 20, pos.getY() + 20, pos.getZ() + 20));
                boolean found = false;
                for (EntityCardDeck deck : closeDecks) {
                    if (deck.getUUID().equals(id)) { found = true; break; }
                }
                if (!found) player.getInventory().getItem(pSlotId).shrink(1);
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Player player = pContext.getPlayer();
        if (player != null && !player.isCrouching()) {
            BlockPos pos = pContext.getClickedPos();
            List<EntityCardDeck> closeDecks = pContext.getLevel().getEntitiesOfClass(EntityCardDeck.class,
                new AABB(pos.getX() - 8, pos.getY() - 8, pos.getZ() - 8, pos.getX() + 8, pos.getY() + 8, pos.getZ() + 8));
            CompoundTag nbt = ItemHelper.getNBT(pContext.getItemInHand());
            if (nbt.hasUUID("UUID")) {
                UUID deckID = nbt.getUUID("UUID");
                for (EntityCardDeck closeDeck : closeDecks) {
                    if (closeDeck.getUUID().equals(deckID)) {
                        Level world = pContext.getLevel();
                        EntityCard card = new EntityCard(world, pContext.getClickLocation(), pContext.getRotation(),
                            nbt.getByte("SkinID"), deckID, nbt.getBoolean("Covered"), (byte) pContext.getItemInHand().getDamageValue());
                        world.addFreshEntity(card);
                        pContext.getItemInHand().shrink(1);
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }
}
