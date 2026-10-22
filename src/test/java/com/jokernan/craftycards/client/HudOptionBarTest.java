package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 选项行的统一排版。两个游戏（斗地主叫分 / 炸金花行动）读同一份 {@link HudOptionBar}，
 * 这里钉住三件事：等宽、居中、窄屏不越界；以及"斗地主的观感不许被改掉"。
 */
class HudOptionBarTest {

    /** 真实文字宽度量级：两字中文 18、带一位数字的「跟注 1」28、16 字符 ID 90。 */
    private static final int[] DDZ_LABELS = {18, 18, 18, 18};
    private static final int[] ZJH_LABELS = {18, 28, 28, 28, 18, 28};
    private static final int[] VIEWPORT_WIDTHS = {320, 427, 640, 960};

    /**
     * 斗地主叫分是四个两字标签：等宽下限 40px，总宽 160，起点 (w-160)/2。
     * 这正是改这一版之前 `renderBidOptions` 里写死的数字——统一之后斗地主的观感必须<b>一点没变</b>。
     */
    @Test
    void ddzBidRowKeepsItsExactPreviousGeometry() {
        for (int w : VIEWPORT_WIDTHS) {
            var items = HudOptionBar.solve(w, DDZ_LABELS, 100);
            assertEquals(4, items.length);
            for (int i = 0; i < 4; i++) {
                assertEquals(40, items[i].width(), "每一项都应是 40px");
                assertEquals(w / 2 - 80 + i * 40, items[i].x(), "起点与步进不变（" + w + " 宽）");
            }
        }
    }

    @Test
    void itemsAreUniformWidthAndCenteredAsAGroup() {
        for (int w : VIEWPORT_WIDTHS) {
            var items = HudOptionBar.solve(w, ZJH_LABELS, 100);
            int first = items[0].width();
            for (var item : items) assertEquals(first, item.width(), "等宽是这套语言的前提");
            // 整组居中：左右留白之差不超过 1px
            int leftGap = items[0].x();
            int rightGap = w - items[items.length - 1].right();
            assertTrue(Math.abs(leftGap - rightGap) <= 1, "整组应居中：" + leftGap + " vs " + rightGap);
            assertEquals(w / 2, (items[0].x() + items[items.length - 1].right()) / 2, "组中心在屏心");
        }
    }

    /** 窄屏：等分压缩、不重叠、每项仍可点，且不出视口。 */
    @Test
    void narrowViewportCompressesInsteadOfOverflowing() {
        int[] longNames = {90, 90, 90, 90, 90};   // 5 个 16 字符 ID
        for (int w : VIEWPORT_WIDTHS) {
            var items = HudOptionBar.solve(w, longNames, 100);
            assertTrue(items[0].focusLeft() >= 0, "焦点框左边界不得越界（" + w + "）");
            assertTrue(items[items.length - 1].focusRight() <= w, "焦点框右边界不得越界（" + w + "）");
            for (int i = 0; i < items.length; i++) {
                assertTrue(items[i].width() >= 10, "每项至少 10px 可点");
                if (i > 0) assertEquals(items[i - 1].right(), items[i].x(), "相邻项紧挨、不重叠也不留缝");
            }
        }
    }

    @Test
    void focusBoxIsSymmetricAroundTheItem() {
        var item = HudOptionBar.solve(640, DDZ_LABELS, 100)[0];
        assertEquals(item.x() - HudOptionBar.FOCUS_PAD, item.focusLeft());
        assertEquals(item.right() + HudOptionBar.FOCUS_PAD, item.focusRight(), "左右外扩对称（斗地主原来是右边界少 2px）");
        assertEquals(item.y() - HudOptionBar.FOCUS_PAD, item.focusTop());
        assertEquals(item.y() + 10, item.focusBottom());
    }

    /** 竖向语法：反馈 → 提示 → 选项，间距来自共享常量，两个游戏一致。 */
    @Test
    void hintAndMessageSitAboveTheOptionRow() {
        int optionsY = 200;
        assertEquals(optionsY - HudOptionBar.HINT_GAP, HudOptionBar.hintY(optionsY));
        assertEquals(HudOptionBar.hintY(optionsY) - HudOptionBar.MESSAGE_GAP, HudOptionBar.messageY(optionsY));
        assertTrue(HudOptionBar.messageY(optionsY) < HudOptionBar.hintY(optionsY));
        assertTrue(HudOptionBar.hintY(optionsY) < optionsY);
    }

    /**
     * 斗地主用固定偏移 h-165 定位操作块。320×180 下那会算出 y=15，压在阶段行（y=16）上、
     * 提示行落到 y=2 盖住标题——这是探针跑出来的真实缺陷。下限兜住之后，
     * 反馈行必须仍然让开顶部三行。
     */
    @Test
    void ddzActionRowNeverCollidesWithTheHeaderOnShortViewports() {
        for (int h : new int[]{180, 200, 220, 240, 360, 540}) {
            int optionsY = HudOptionBar.actionRowY(h, DDZGameHud.ACTION_ROW_OFFSET);
            assertTrue(HudOptionBar.messageY(optionsY) >= HudOptionBar.HEADER_SAFE_Y,
                h + " 高：反馈行 " + HudOptionBar.messageY(optionsY) + " 应 ≥ " + HudOptionBar.HEADER_SAFE_Y);
            if (h - DDZGameHud.ACTION_ROW_OFFSET >= HudOptionBar.minOptionRowY()) {
                assertEquals(h - DDZGameHud.ACTION_ROW_OFFSET, optionsY, h + " 高：正常屏幕位置不变");
            }
        }
    }

    @Test
    void colorsMatchTheSharedPalette() {
        assertEquals(HudOptionBar.COLOR_FOCUS, HudOptionBar.colorOf(true, true));
        assertEquals(HudOptionBar.COLOR_NORMAL, HudOptionBar.colorOf(false, true));
        assertEquals(HudOptionBar.COLOR_DISABLED, HudOptionBar.colorOf(true, false), "不可选时即使有焦点也置灰");
        assertEquals(0, HudOptionBar.solve(640, new int[0], 100).length, "没有选项时返回空数组");
    }
}
