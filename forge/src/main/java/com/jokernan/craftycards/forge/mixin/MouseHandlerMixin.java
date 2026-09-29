package com.jokernan.craftycards.forge.mixin;

import com.jokernan.craftycards.client.DDZGameHud;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版鼠标处理前把按键与滚轮交给公共 HUD，消费操作时阻止攻击或切换物品栏。 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void crafty_cards$onPress(long windowPointer, int button, int action, int modifiers,
                                      CallbackInfo ci) {
        if (DDZGameHud.getInstance().handleMouseClick(button, action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void crafty_cards$onScroll(long windowPointer, double xOffset, double yOffset,
                                       CallbackInfo ci) {
        if (DDZGameHud.getInstance().handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}



