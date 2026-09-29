package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「上轮出牌」HUD 块布局的不变量测试。
 *
 * <p>这块内容的位置由窗口宽度、手牌宽度（手牌数）和出牌张数共同决定，靠肉眼看画面很难覆盖到
 * 边界组合（窄窗口 + 少手牌 + 一次出 20 张）。这里穷举各类组合，断言两条不变量：
 * 整块不出屏、牌行不压住手牌。</p>
 */
class HudPlayedLayoutTest {
    /** 默认布局参数，与 RenderConfig 的默认值一致。 */
    private static final HudPlayedLayout.Config CFG =
        new HudPlayedLayout.Config(30, 12, 6, 12, 18, 10, 6);

    /** 手牌区右边缘：按 DDZGameHud.handStartX/handEndX 的同一算法由屏宽与手牌数推导。 */
    private static int handEndX(int screenWidth, int handCount) {
        int cardW = 40, handGap = 40, minGap = 18;
        double ratio = 0.9;
        int gap = handCount <= 1
            ? handGap
            : Math.min(handGap, Math.max(minGap, ((int) (screenWidth * ratio) - cardW) / (handCount - 1)));
        int totalW = gap * (handCount - 1) + cardW;
        return screenWidth / 2 + totalW / 2;
    }

    /** 手牌区顶边：与 DDZGameHud.handBaseY 一致（屏高 - 底边距 - 牌高）。 */
    private static int handTop(int screenHeight) {
        return screenHeight - 30 - Math.round(40 * 16F / 10.2F);
    }

    @Test
    void blockAlwaysFitsOnScreen() {
        // 窗口宽度从原版最小 GUI 宽（320）到宽屏；手牌数与出牌张数覆盖 1..20 的全部现实取值
        for (int width = 320; width <= 1280; width++) {
            for (int height : new int[]{240, 270, 360, 480, 720}) {
                for (int handCount : new int[]{1, 2, 5, 10, 17, 20}) {
                    for (int playedCount : new int[]{1, 2, 3, 5, 12, 20}) {
                        for (int labelWidth : new int[]{90, 110, 130, 160}) {
                            HudPlayedLayout l = HudPlayedLayout.solve(
                                width, handEndX(width, handCount), handTop(height),
                                playedCount, labelWidth, CFG);
                            String where = String.format(
                                "屏 %dx%d 手牌 %d 张 出牌 %d 张 标签宽 %d → 标签x=%d 牌行左=%d 右=%d",
                                width, height, handCount, playedCount, labelWidth,
                                l.labelX(), l.rowLeft(), l.right());
                            assertTrue(l.labelX() >= 0, "标签出屏左：" + where);
                            assertTrue(l.rowLeft() >= 0, "牌行出屏左：" + where);
                            assertTrue(l.right() <= width, "整块出屏右：" + where);
                        }
                    }
                }
            }
        }
    }

    @Test
    void rowNeverCoversHand() {
        for (int width = 320; width <= 1280; width += 7) {
            for (int height : new int[]{240, 270, 360, 480, 720}) {
                for (int playedCount : new int[]{1, 5, 20}) {
                    HudPlayedLayout l = HudPlayedLayout.solve(
                        width, handEndX(width, 17), handTop(height), playedCount, 130, CFG);
                    // 选中牌会从手牌顶再上移 hudSelectLift(14)，牌行底边须高于它
                    int lifted = handTop(height) - 14;
                    assertTrue(l.rowBottom() <= lifted,
                        "牌行压住选中牌：屏 " + width + "x" + height + " 出牌 " + playedCount
                            + " → 牌行底 " + l.rowBottom() + " 选中牌顶 " + lifted);
                }
            }
        }
    }

    @Test
    void cardsShrinkGapInsteadOfOverflowing() {
        // 一次出 20 张：间距应被压缩到配置值以下，但仍不低于下限
        HudPlayedLayout l = HudPlayedLayout.solve(480, handEndX(480, 1), handTop(270), 20, 130, CFG);
        assertTrue(l.gap() < CFG.cardGap(), "牌多时应收紧间距，实际 gap=" + l.gap());
        assertTrue(l.gap() >= CFG.minGap(), "间距不应低于下限，实际 gap=" + l.gap());
    }

    @Test
    void fewCardsKeepConfiguredGapAndSitAboveHand() {
        // 手牌满宽（17 张）、出牌 3 张：间距用配置值，整块贴在手牌右上方
        int width = 640;
        HudPlayedLayout l = HudPlayedLayout.solve(width, handEndX(width, 17), handTop(360), 3, 110, CFG);
        assertEquals(CFG.cardGap(), l.gap());
        assertTrue(l.right() <= width - CFG.edgeMargin(), "应保留右边距");
        assertTrue(l.rowBottom() < handTop(360), "整块应在手牌上方");
        assertTrue(l.labelX() < l.rowLeft(), "标签应在牌行左侧");
    }

    @Test
    void narrowWindowWithFewCardsPushesBlockRightToStayVisible() {
        // 手牌只有 1 张时手牌右缘靠近屏幕中心，空间不够→整块须右移到边距内而不是出屏
        int width = 427;
        HudPlayedLayout l = HudPlayedLayout.solve(width, handEndX(width, 1), handTop(240), 20, 150, CFG);
        assertTrue(l.labelX() >= 0, "标签出屏左：" + l.labelX());
        assertTrue(l.right() <= width, "整块出屏右：" + l.right());
    }

    @Test
    void rowHeightMatchesCardModelAspect() {
        // 卡牌模型 x 宽 10.2、z 长 16，故渲染高度的 / 宽 比例 = 16/10.2
        for (int cardW : new int[]{20, 30, 40, 60}) {
            assertEquals(Math.round(cardW * 16F / 10.2F), HudPlayedLayout.rowHeight(cardW));
        }
    }
}
