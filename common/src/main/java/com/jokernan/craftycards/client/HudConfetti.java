package com.jokernan.craftycards.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * HUD 共用的小装饰：结算礼花。
 *
 * <p>存在的理由：斗地主和炸金花的结算都要放同一套礼花。原先这段在 {@code DDZGameHud} 里是私有的，
 * 炸金花要"风格统一"就只能复制一份——两份随机种子/颜色表一旦改歪就会变成两种观感。</p>
 *
 * <p>固定种子逐帧重置，所以图案静止不闪烁；复用同一个 {@link Random} 实例，避免每帧分配。</p>
 */
public final class HudConfetti {
    private static final Random RANDOM = new Random();
    private static final int[] COLORS = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF};
    private static final int COUNT = 50;

    private HudConfetti() {}

    /** 在整屏范围内撒一层彩色方块（只在玩家获胜时调用）。 */
    public static void render(GuiGraphics guiGraphics, int width, int height) {
        RANDOM.setSeed(12345);
        for (int i = 0; i < COUNT; i++) {
            int x = RANDOM.nextInt(Math.max(1, width));
            int y = RANDOM.nextInt(Math.max(1, height));
            int size = 2 + RANDOM.nextInt(4);
            guiGraphics.fill(x, y, x + size, y + size, COLORS[RANDOM.nextInt(COLORS.length)]);
        }
    }

    /** 结算遮罩：两个游戏共用同一层半透明黑，避免"一个偏灰一个偏黑"。 */
    public static void scrim(GuiGraphics guiGraphics, int width, int height) {
        guiGraphics.fill(0, 0, width, height, 0xA0000000);
    }
}
