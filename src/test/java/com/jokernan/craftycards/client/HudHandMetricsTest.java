package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HUD 手牌布局的测试。
 *
 * <p>守的是用户实际遇到的那个问题：手牌多时<b>最左侧那张的牌角甩出屏幕</b>，看不到点数。
 * 原因是布局只算了牌的矩形宽度、没算扇形旋转的外扩量。</p>
 */
class HudHandMetricsTest {
    /** 用户的实际参数（visual.json）。 */
    private static final int CARD_W = 40;
    private static final int CARD_H = Math.round(CARD_W * 16F / 10.2F);   // ≈ 63
    private static final int HAND_GAP = 40;
    private static final int MIN_GAP = 18;
    private static final float MAX_RATIO = 0.94F;
    private static final float FAN = 20F;

    private static HudHandMetrics.Layout solve(int width, int count) {
        return HudHandMetrics.solve(width, count, CARD_W, CARD_H, HAND_GAP, MIN_GAP, MAX_RATIO, FAN);
    }

    @Test
    void fullscreenKeepsHudCardAndDipaiProportions() {
        HudHandMetrics.Viewport small = HudHandMetrics.viewport(480, 320);
        HudHandMetrics.Viewport full = HudHandMetrics.viewport(960, 540);
        assertEquals(1F, small.scale(), 0.001F);
        assertEquals(320, full.height());
        assertEquals(CARD_W / 320F, CARD_W * full.scale() / 540F, 0.001F);
        assertEquals(126 / 320F, 126 * full.scale() / 540F, 0.001F);
    }

    /** 各种屏宽 / 张数下，含扇形外扩的整片手牌都必须落在屏幕内。 */
    @Test
    void handNeverOverflowsScreenEvenWithFan() {
        for (int width = 320; width <= 1920; width += 1) {
            for (int count = 1; count <= 20; count++) {
                HudHandMetrics.Layout l = solve(width, count);
                String where = "屏宽 " + width + " 张数 " + count;
                assertTrue(l.visualLeft() >= 0, "手牌左侧越界：" + where + " 左=" + l.visualLeft());
                assertTrue(l.visualRight() <= width,
                    "手牌右侧越界：" + where + " 右=" + l.visualRight());
            }
        }
    }

    /** 扇形为 0（牌不倾斜）时不应预留外扩量。 */
    @Test
    void noFanMeansNoOverhang() {
        assertEquals(0, HudHandMetrics.fanOverhang(CARD_W, CARD_H, 0F));
        HudHandMetrics.Layout flat = HudHandMetrics.solve(
            427, 13, CARD_W, CARD_H, HAND_GAP, MIN_GAP, MAX_RATIO, 0F);
        HudHandMetrics.Layout fanned = solve(427, 13);
        assertTrue(fanned.overhang() > 0, "有扇形角时必须预留外扩量");
        assertTrue(fanned.gap() <= flat.gap(), "为外扩让位后间距应不大于无扇形时");
    }

    /** 外扩量随倾角单调增大，且大致等于牌高乘 sin(θ)。 */
    @Test
    void overhangGrowsWithFanAngle() {
        int previous = -1;
        for (float fan = 0F; fan <= 40F; fan += 5F) {
            int overhang = HudHandMetrics.fanOverhang(CARD_W, CARD_H, fan);
            assertTrue(overhang >= previous, "外扩量应随倾角单调不减：fan=" + fan);
            previous = overhang;
        }
        // 20° 时每侧约 63×sin20° ≈ 21.5px，两侧合计 ≈ 43px
        int at20 = HudHandMetrics.fanOverhang(CARD_W, CARD_H, 20F);
        assertTrue(at20 >= 38 && at20 <= 50, "20° 的外扩量应在 38~50px 之间，实际 " + at20);
        // 正负倾角对称
        assertEquals(at20, HudHandMetrics.fanOverhang(CARD_W, CARD_H, -20F));
    }

    /**
     * 间距是<b>两层下限</b>：空间够时不低于首选下限；连首选下限都放不下时才继续压到硬下限
     * （宁可轻微重叠，也好过最外侧的牌跑出屏幕）。
     */
    @Test
    void gapUsesTwoTierMinimum() {
        // 宽屏、牌不多 → 用默认间距
        assertTrue(solve(1920, 5).gap() == HAND_GAP, "空间充裕应用默认间距");
        // 中等宽度 → 压缩但不低于首选下限
        HudHandMetrics.Layout mid = solve(640, 17);
        assertTrue(mid.gap() >= MIN_GAP, "空间够时应不低于首选下限，实际 " + mid.gap());
        // 窄屏 + 满手牌 → 允许低于首选下限，但不得低于硬下限（且不得越界）
        HudHandMetrics.Layout tight = solve(320, 20);
        assertTrue(tight.gap() >= HudHandMetrics.HARD_MIN_GAP,
            "不应低于硬下限，实际 " + tight.gap());
        assertTrue(tight.gap() <= HAND_GAP);
        assertTrue(tight.visualLeft() >= 0 && tight.visualRight() <= 320);
    }

    /** 单张牌居中放置。 */
    @Test
    void singleCardIsCentered() {
        HudHandMetrics.Layout l = solve(800, 1);
        assertEquals(800 / 2 - CARD_W / 2, l.startX());
        assertEquals(CARD_W, l.totalWidth());
    }

    /** 模型厚度 0.5/16，GUI 的 z 缩放为 1。后牌必须完整压过前牌，不能共面。 */
    @Test
    void overlappingCardsHaveDisjointDepthLayers() {
        float modelThickness = 0.5F / 16F;
        for (int i = 0; i < 53; i++) {
            assertTrue(HudHandMetrics.cardDepth(i + 1) > HudHandMetrics.cardDepth(i) + modelThickness,
                "相邻卡牌深度不能交叠：" + i);
        }
    }

    /** 焦点框贴在本张牌前面，但不能穿到右侧覆盖它的牌上。 */
    @Test
    void focusOutlineRespectsCardOcclusion() {
        for (int i = 0; i < 53; i++) {
            assertTrue(HudHandMetrics.focusDepth(i) > HudHandMetrics.cardDepth(i) + 0.5F / 16F);
            assertTrue(HudHandMetrics.focusDepth(i) < HudHandMetrics.cardDepth(i + 1));
        }
    }
}
