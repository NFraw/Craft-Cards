package com.jokernan.craftycards.entity;

import com.jokernan.craftycards.entity.base.EntityStacked;
import com.jokernan.craftycards.init.InitEntityTypes;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.util.ChatHelper;
import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EntityCardDeck extends EntityStacked {
    private static final EntityDataAccessor<Float> ROTATION = SynchedEntityData.defineId(EntityCardDeck.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> SKIN_ID = SynchedEntityData.defineId(EntityCardDeck.class, EntityDataSerializers.BYTE);

    public EntityCardDeck(EntityType<? extends EntityCardDeck> type, Level world) {
        super(type, world);
    }

    public EntityCardDeck(Level world, Vec3 position, float rotation, byte skinID) {
        super(InitEntityTypes.CARD_DECK.get(), world, position);
        createAndFillDeck();
        shuffleStack();
        this.entityData.set(ROTATION, rotation);
        this.entityData.set(SKIN_ID, skinID);
    }

    public float getRotation() { return this.entityData.get(ROTATION); }
    public byte getSkinID() { return this.entityData.get(SKIN_ID); }

    private void createAndFillDeck() {
        Byte[] newStack = new Byte[54];
        for (byte index = 0; index < 54; index++) newStack[index] = index;
        this.entityData.set(STACK, newStack);
    }

    @Override
    public InteractionResult interact(Player pPlayer, InteractionHand pHand) {
        if (pHand == InteractionHand.MAIN_HAND) {
            if (getStackAmount() > 0) {
                int cardID = getTopStackID();
                ItemStack card = new ItemStack(InitItems.CARD_COVERED.get());
                card.setDamageValue(cardID);
                CompoundTag nbt = ItemHelper.getOrCreateNBT(card);
                nbt.putUUID("UUID", getUUID());
                nbt.putByte("SkinID", this.entityData.get(SKIN_ID));
                nbt.putBoolean("Covered", true);
                ItemHelper.setNBT(card, nbt);
                if (!level().isClientSide) ItemHelper.spawnStackAtEntity(level(), pPlayer, card);
                removeFromTop();
                return pPlayer.getMainHandItem().isEmpty() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            } else if (level().isClientSide) {
                ChatHelper.printModMessage(ChatFormatting.RED, Component.translatable("message.stack_empty"), pPlayer);
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (pSource.getDirectEntity() instanceof Player player) {
            if (player.isCrouching()) {
                // 牌堆物品已下线，潜行击打不再掉出牌堆物品
                discard();
            } else {
                shuffleStack();
                if (level().isClientSide) ChatHelper.printModMessage(ChatFormatting.GREEN, Component.translatable("message.stack_shuffled"), player);
            }
            return true;
        }
        return false;
    }

    @Override
    public void moreData(SynchedEntityData.Builder builder) {
        builder.define(ROTATION, 0F);
        builder.define(SKIN_ID, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(ROTATION, tag.getFloat("Rotation"));
        this.entityData.set(SKIN_ID, tag.getByte("SkinID"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Rotation", this.entityData.get(ROTATION));
        tag.putByte("SkinID", this.entityData.get(SKIN_ID));
    }
}
