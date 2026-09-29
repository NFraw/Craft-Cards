package com.jokernan.craftycards.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.item.ItemStack;

/** A render snapshot contains copied presentation data, never a live entity. */
public final class CardEntityRenderState extends EntityRenderState {
    public ItemStack card = ItemStack.EMPTY;
    public int amount;
    public float rotation;
}
