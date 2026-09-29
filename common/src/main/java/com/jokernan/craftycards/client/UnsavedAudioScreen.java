package com.jokernan.craftycards.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 关闭设置界面时还有未保存的音频改动 —— 三选一确认。
 *
 * <p>不做这个确认的话，"选了个音乐包 / 调了音量 → 想再看看效果 → 直接关掉界面"就会静默丢掉改动，
 * 而音频的保存本身是重活（要重建资源包并重载），不可能改成每次改动都立刻落地。
 * 与其让玩家莫名其妙地白改，不如关界面前问一句。</p>
 *
 * <p>ESC 等同于「继续编辑」（最安全的默认），不会误丢改动。</p>
 */
public class UnsavedAudioScreen extends Screen {
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_GAP = 6;

    /** 保存 / 放弃之后回到的界面（通常是游戏或 Mods 列表）。 */
    private final Screen returnTo;
    /** 「继续编辑」回到的界面（设置主页）。 */
    private final Screen backTo;

    public UnsavedAudioScreen(Screen returnTo, Screen backTo) {
        super(Component.literal("音频改动尚未保存"));
        this.returnTo = returnTo;
        this.backTo = backTo;
    }

    @Override
    protected void init() {
        int cx = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 10;
        addRenderableWidget(Button.builder(Component.literal("保存并应用"), b -> {
            SoundEditor.commit();
            SoundEditor.discard();
            if (minecraft != null) minecraft.setScreen(returnTo);
        }).pos(cx, y).size(BUTTON_WIDTH, 20).build());

        y += 20 + BUTTON_GAP;
        addRenderableWidget(Button.builder(Component.literal("放弃改动"), b -> {
            SoundEditor.discard();
            if (minecraft != null) minecraft.setScreen(returnTo);
        }).pos(cx, y).size(BUTTON_WIDTH, 20).build());

        y += 20 + BUTTON_GAP;
        addRenderableWidget(Button.builder(Component.literal("继续编辑"), b -> {
            if (minecraft != null) minecraft.setScreen(backTo);
        }).pos(cx, y).size(BUTTON_WIDTH, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font,
            "你在音频设置里改了东西（音乐包/音量/总开关）但还没保存，直接关掉会丢掉这些改动。",
            width / 2, height / 2 - 34, 0xFFAAAAAA);
        guiGraphics.drawCenteredString(font,
            "「保存并应用」会写入配置并重载音频资源。",
            width / 2, height / 2 - 22, 0xFF808080);
    }

    @Override
    public void onClose() {
        // ESC = 继续编辑，绝不静默丢改动
        if (minecraft != null) minecraft.setScreen(backTo);
    }
}
