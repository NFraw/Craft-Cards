package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置界面排版的单测：钉住"不重叠、不越界"这两条底线。
 *
 * <p>改版前音频界面底部一行塞了 7 个按固定 x 摆放的控件，窗口一小就叠在一起；
 * 列表高度还是手填的魔数，最后一行会被状态文字压住。这些都是"打开游戏才看得见"的问题，
 * 而排版本身是纯算术——所以把算术抽到 {@link ConfigLayout} 里，用穷举分辨率的方式测。</p>
 */
class ConfigLayoutTest {
    /** 覆盖从最小可用窗口到超宽屏的各种分辨率。 */
    private static final int[] WIDTHS = {320, 427, 640, 854, 1280, 1920, 2560};
    private static final int[] HEIGHTS = {240, 320, 480, 720, 1080, 1440};

    /** 列表必须整个落在"标题下方、底部区上方"之间。 */
    @Test
    void listNeverOverlapsTitleOrFooter() {
        for (int h : HEIGHTS) {
            int listTop = ConfigLayout.LIST_TOP;
            int listBottom = listTop + ConfigLayout.listHeight(h);
            assertTrue(listBottom <= ConfigLayout.footerY(h) - ConfigLayout.LIST_BOTTOM_MARGIN,
                "列表底边越过了底部区: h=" + h);
            assertTrue(listTop >= 30, "列表顶边压住标题/面包屑: h=" + h);
        }
    }

    /** 状态文字与按钮各占一行，互不重叠、也都不出屏幕。 */
    @Test
    void statusTextAndButtonsDoNotCollide() {
        for (int h : HEIGHTS) {
            int statusBottom = ConfigLayout.statusY(h) + ConfigLayout.LINE_HEIGHT;
            assertTrue(statusBottom <= ConfigLayout.buttonY(h),
                "状态文字压住底部按钮: h=" + h + " statusBottom=" + statusBottom
                    + " buttonY=" + ConfigLayout.buttonY(h));
            assertTrue(ConfigLayout.buttonY(h) + 20 <= h,
                "底部按钮超出屏幕: h=" + h);
        }
    }

    /** 底部按钮整排必须装得进屏幕（两个按钮时是最常见的 100+8+100=208 宽）。 */
    @Test
    void footerButtonsFitScreen() {
        for (int w : WIDTHS) {
            int x = ConfigLayout.footerButtonsX(w, 100, 100);
            assertTrue(x >= 0, "按钮排到屏幕左边之外: w=" + w);
            assertTrue(x + ConfigLayout.controlsWidth(100, 100) <= w,
                "按钮超出屏幕右边: w=" + w);
        }
    }

    /** 行内文字与控件不许相交——这正是"标签太长盖住按钮"的那种问题。 */
    @Test
    void rowTextNeverOverlapsControls() {
        int rowWidth = 854 - 40;
        // 音频槽位行：文件按钮 + 试听
        int[] controls = {150, 44};
        int controlsTotal = ConfigLayout.controlsWidth(controls);
        int room = ConfigLayout.textRoom(rowWidth, controlsTotal);
        int controlsStart = ConfigLayout.controlsX(20, rowWidth, controlsTotal);
        assertTrue(20 + ConfigLayout.ROW_PADDING + room <= controlsStart,
            "文字区与控件区相交");
        assertTrue(controlsStart + controlsTotal <= 20 + rowWidth - ConfigLayout.ROW_PADDING + 1,
            "控件超出行的右边界");
    }

    /** 行高至少要放得下"标签 + 提示"两行文字。 */
    @Test
    void rowIsTallEnoughForTwoLines() {
        assertTrue(ConfigLayout.ROW_HEIGHT >= ConfigLayout.minRowHeightForTwoLines(),
            "行高放不下两行文字");
    }

    /** 多控件行的逐个 x 必须递增且间距一致（右对齐排布）。 */
    @Test
    void controlsAreLaidOutLeftToRightFromTheRightEdge() {
        int[] widths = {150, 44};
        int first = ConfigLayout.controlX(20, 800, 0, widths);
        int second = ConfigLayout.controlX(20, 800, 1, widths);
        assertEquals(first + widths[0] + ConfigLayout.CONTROL_GAP, second);
        assertEquals(20 + 800 - ConfigLayout.ROW_PADDING, second + widths[1],
            "最后一个控件的右缘应贴着行右边界");
    }

    /** 极小窗口下不出现负高度/负宽度（宁可列表为 0，也不能算出负数把渲染搞崩）。 */
    @Test
    void degenerateWindowKeepsNumbersNonNegative() {
        assertEquals(0, ConfigLayout.listHeight(10));
        assertTrue(ConfigLayout.textRoom(40, 200) >= 0);
    }
}
