package com.jokernan.craftycards.fabric.mixin;

import com.jokernan.craftycards.client.DDZGameHud;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鼠标输入接缝：把"要不要拦截这次鼠标输入"问给 common 的 HUD，再决定是否取消原版处理。
 *
 * <p><b>为什么这两个必须 mixin</b>：Fabric API 没有原始鼠标按键/滚轮事件
 * （{@code UseBlockCallback} 那类只覆盖"对着方块用"，而这里要拦的是"玩家在牌局里滚轮选牌、
 * 左键确认、右键清空选择"，且要阻止原版继续处理——滚轮切热键栏、左键破坏方块）。
 * NeoForge 侧对应的是 {@code InputEvent.MouseButton.Pre} 与
 * {@code InputEvent.MouseScrollingEvent}。</p>
 *
 * <p>判断逻辑（什么阶段拦、握着什么才拦）全在 {@link DDZGameHud#handleMouseClick} /
 * {@link DDZGameHud#handleScroll}，这里只做两件事：取参数、注入点 HEAD 处按结果取消。</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /** 鼠标按下（button：0=左键 1=右键；action：1=按下）。拦了就不让原版继续处理。 */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void crafty_cards$onPress(long windowPointer, int button, int action, int modifiers,
                                      CallbackInfo ci) {
        if (DDZGameHud.getInstance().handleMouseClick(button, action)) {
            ci.cancel();
        }
    }

    /** 滚轮（yOffset = 原版滚轮增量）。没握牌时不拦，滚轮照常切热键栏。 */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void crafty_cards$onScroll(long windowPointer, double xOffset, double yOffset,
                                       CallbackInfo ci) {
        if (DDZGameHud.getInstance().handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}
