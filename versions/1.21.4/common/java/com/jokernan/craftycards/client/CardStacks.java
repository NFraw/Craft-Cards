package com.jokernan.craftycards.client;

import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.init.InitModelOverrides;
import net.minecraft.world.item.ItemStack;

/** Shared render stacks are immutable to callers; damage dispatch selects faces. */
final class CardStacks {
    private static final ItemStack[] FACES = new ItemStack[54];
    private static ItemStack covered;
    static void probeCachedOnce() { InitModelOverrides.probeOnce(); }
    static ItemStack face(int id) {
        if (id < 0 || id >= FACES.length) throw new IllegalArgumentException("Invalid card id: " + id);
        if (FACES[id] == null) {
            ItemStack stack = new ItemStack(InitItems.CARD.get());
            stack.setDamageValue(id);
            FACES[id] = stack;
        }
        return FACES[id];
    }
    static ItemStack covered() {
        if (covered == null) covered = new ItemStack(InitItems.CARD_COVERED.get());
        return covered;
    }
}
