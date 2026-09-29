package com.jokernan.craftycards.forge.mixin;

import com.jokernan.craftycards.client.DDZGameHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 52 removed RenderGuiEvent; draw after the vanilla layered HUD. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void crafty_cards$hud(GuiGraphics graphics, DeltaTracker timer, CallbackInfo ci) {
        DDZGameHud.getInstance().render(graphics, timer);
    }
}
