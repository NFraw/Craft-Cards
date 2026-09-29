package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算界面竖排布局的不变量测试。
 *
 * <p>这块排布此前被手调过两次（"第二三家上移 1.5 倍行高"），每次都引入新的重叠：
 * 三家顺序被搅乱、牌行压在押注信息上。这类问题靠肉眼在游戏里很难一次看全（窗口高度不同
 * 表现也不同），故在此穷举窗口高度与押注行数，把"任何一行都不与相邻行重叠、都在屏内"
 * 固定住。</p>
 */
class HudSettlementLayoutTest {
    private static final int LINE_H = 9;
    /** 结算小牌行的渲染高度：hudSmallCardW(22) × 16 / 10.2 ≈ 34.5 → 35。 */
    private static final int CARD_ROW_H = Math.round(22 * 16F / 10.2F);

    /** 任意窗口高度下，结果/押注/三家/底部提示都不越出屏幕。 */
    @Test
    void everythingStaysOnScreen() {
        for (int h = 240; h <= 1080; h++) {
            for (int betLines = 0; betLines <= 2; betLines++) {
                HudSettlementLayout l = HudSettlementLayout.solve(h, LINE_H, CARD_ROW_H, betLines);
                String where = "屏高 " + h + " 押注行数 " + betLines;
                assertTrue(l.resultY >= 0, "结果行越出屏幕顶部：" + where);
                assertTrue(l.closeY + LINE_H <= h, "底部提示越出屏幕：" + where + " closeY=" + l.closeY);
                for (int i = 0; i < 3; i++) {
                    assertTrue(l.nameY[i] >= 0, "第 " + i + " 家文字越出顶部：" + where);
                    int cardsBottom = l.cardsCenterY[i] + CARD_ROW_H / 2;
                    assertTrue(cardsBottom <= h, "第 " + i + " 家牌行越出底部：" + where + " 牌底=" + cardsBottom);
                }
            }
        }
    }

    /** 结果行 → 押注行 → 输赢行 → 三家，自上而下不重叠。 */
    @Test
    void rowsNeverOverlap() {
        for (int h = 240; h <= 1080; h++) {
            for (int betLines = 0; betLines <= 2; betLines++) {
                HudSettlementLayout l = HudSettlementLayout.solve(h, LINE_H, CARD_ROW_H, betLines);
                String where = "屏高 " + h + " 押注行数 " + betLines;

                int cursor = l.resultY + LINE_H;
                if (l.betY >= 0) {
                    assertTrue(l.betY >= cursor, "押注行与结果行重叠：" + where);
                    cursor = l.betY + LINE_H;
                }
                if (l.betWinY >= 0) {
                    assertTrue(l.betWinY >= cursor, "输赢行与押注行重叠：" + where);
                    cursor = l.betWinY + LINE_H;
                }
                for (int i = 0; i < 3; i++) {
                    assertTrue(l.nameY[i] >= cursor, "第 " + i + " 家文字与上方内容重叠：" + where);
                    int cardsTop = l.cardsCenterY[i] - CARD_ROW_H / 2;
                    assertTrue(cardsTop >= l.nameY[i] + LINE_H, "第 " + i + " 家牌行压住自己的名字：" + where);
                    cursor = l.cardsCenterY[i] + CARD_ROW_H / 2;
                }
                assertTrue(l.closeY >= cursor, "底部提示与最后一家重叠：" + where);
            }
        }
    }

    /** 三家的顺序必须是 0、1、2（曾经被打乱成 1、0、2）。 */
    @Test
    void seatsKeepTheirOrder() {
        for (int h = 240; h <= 1080; h += 3) {
            HudSettlementLayout l = HudSettlementLayout.solve(h, LINE_H, CARD_ROW_H, 2);
            assertTrue(l.nameY[0] < l.nameY[1], "第 0 家应排在第 1 家之上（屏高 " + h + "）");
            assertTrue(l.nameY[1] < l.nameY[2], "第 1 家应排在第 2 家之上（屏高 " + h + "）");
        }
    }

    /** 无押注时不出现押注行，且整体居中不受影响。 */
    @Test
    void noBetMeansNoBetLines() {
        for (int h = 240; h <= 1080; h += 7) {
            HudSettlementLayout l = HudSettlementLayout.solve(h, LINE_H, CARD_ROW_H, 0);
            assertTrue(l.betY < 0, "无押注时不应有押注行（屏高 " + h + "）");
            assertTrue(l.betWinY < 0, "无押注时不应有输赢行（屏高 " + h + "）");
            assertTrue(l.nameY[0] > l.resultY + LINE_H, "第一家应在结果行下方（屏高 " + h + "）");
        }
    }

    /** 有押注时押注行确实存在，且两家信息都在结果行与第一家之间。 */
    @Test
    void twoBetLinesSitBetweenResultAndFirstSeat() {
        for (int h = 240; h <= 1080; h += 11) {
            HudSettlementLayout l = HudSettlementLayout.solve(h, LINE_H, CARD_ROW_H, 2);
            assertTrue(l.betY >= l.resultY + LINE_H, "押注行位置不对（屏高 " + h + "）");
            assertTrue(l.betWinY >= l.betY + LINE_H, "输赢行位置不对（屏高 " + h + "）");
            assertTrue(l.nameY[0] > l.betWinY, "第一家应在输赢行下方（屏高 " + h + "）");
        }
    }

    /** 最矮的窗口（GUI 高 240，原版下限附近）也必须排得下。 */
    @Test
    void narrowestScreenStillFits() {
        HudSettlementLayout l = HudSettlementLayout.solve(240, LINE_H, CARD_ROW_H, 2);
        int lastRowBottom = l.cardsCenterY[2] + CARD_ROW_H / 2;
        assertTrue(lastRowBottom < 240, "最矮窗口下最后一家被挤出屏幕：" + lastRowBottom);
        assertTrue(l.closeY >= lastRowBottom, "最矮窗口下底部提示与内容重叠");
        assertTrue(l.closeY + LINE_H <= 240, "最矮窗口下底部提示越界");
    }
}
