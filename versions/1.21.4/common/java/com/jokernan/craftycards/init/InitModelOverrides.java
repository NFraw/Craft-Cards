package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import net.minecraft.client.Minecraft;

/** Since 1.21.4 item selection is declared in assets/crafty_cards/items. */
public final class InitModelOverrides {
    private static boolean probed;
    public static void init() {
        CCReference.LOG.info("Card models use item definitions: damage range dispatch and skin item models");
    }
    public static void probeOnce() {
        if (probed) return;
        var resources = Minecraft.getInstance().getResourceManager();
        if (resources.getResource(CCReference.location("items/card.json")).isEmpty()) return;
        probed = true;
        CCReference.LOG.info("Card item definition loaded; 54 face thresholds are generated from the shared card resources");
    }
}
