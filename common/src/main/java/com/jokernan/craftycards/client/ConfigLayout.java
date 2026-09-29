package com.jokernan.craftycards.client;

/**
 * 配置界面的排版计算 —— 纯 Java，可直接单测（不引用任何 Minecraft 类型）。
 *
 * <p>抽出来的理由：改版前的音频界面把 7 个控件塞在底部一行、按固定 x 摆放，
 * 小窗口下按钮直接叠在一起；列表高度也是手填的魔数，"最后一行被状态文字压住"这种问题
 * 只有打开界面才看得出来。把"行/列表/底部按钮该放哪"收成一组纯函数，就能用单测钉住
 * <b>不重叠、不越界</b>这两条底线。</p>
 *
 * <p>坐标语义：屏幕左上角为原点；{@code y} 向下增大。</p>
 */
public final class ConfigLayout {
    /** 设置行高度（容得下标签与提示两行文字）。 */
    public static final int ROW_HEIGHT = 26;
    /** 列表顶边（让开标题与面包屑）。 */
    public static final int LIST_TOP = 32;
    /** 列表底边与底部区之间的空隙。 */
    public static final int LIST_BOTTOM_MARGIN = 4;
    /** 底部区高度：一行状态文字 + 一行按钮。 */
    public static final int FOOTER_HEIGHT = 46;
    /** 状态文字在底部区内的纵向偏移。 */
    public static final int STATUS_OFFSET_Y = 4;
    /** 按钮在底部区内的纵向偏移（排在状态文字下面一行）。 */
    public static final int BUTTON_OFFSET_Y = 18;
    /** 同一行里相邻控件之间的水平间距。 */
    public static final int CONTROL_GAP = 6;
    /** 行内左右两侧留白。 */
    public static final int ROW_PADDING = 8;
    /** 行内两行文字的行距（标签 y+3、提示 y+15）。 */
    public static final int HINT_OFFSET_Y = 15;
    /** 一行文字的高度（原版字体 9px）。 */
    public static final int LINE_HEIGHT = 9;

    private ConfigLayout() {}

    /** 底部区顶边。 */
    public static int footerY(int screenHeight) {
        return screenHeight - FOOTER_HEIGHT;
    }

    /** 列表高度（>=0：极小窗口下不允许算出负高度）。 */
    public static int listHeight(int screenHeight) {
        return Math.max(0, footerY(screenHeight) - LIST_BOTTOM_MARGIN - LIST_TOP);
    }

    /** 状态文字基线 y。 */
    public static int statusY(int screenHeight) {
        return footerY(screenHeight) + STATUS_OFFSET_Y;
    }

    /** 底部按钮顶边 y。 */
    public static int buttonY(int screenHeight) {
        return footerY(screenHeight) + BUTTON_OFFSET_Y;
    }

    /** 一排控件（含间距）的总宽度；无控件时为 0。 */
    public static int controlsWidth(int... controlWidths) {
        if (controlWidths.length == 0) return 0;
        int total = CONTROL_GAP * (controlWidths.length - 1);
        for (int w : controlWidths) total += w;
        return total;
    }

    /**
     * 行内控件的起始 x（右对齐）。
     *
     * @param rowX     行的左边界
     * @param rowWidth 行宽
     */
    public static int controlsX(int rowX, int rowWidth, int controlsTotalWidth) {
        return rowX + rowWidth - ROW_PADDING - controlsTotalWidth;
    }

    /**
     * 第 {@code index} 个控件的 x（在 {@link #controlsX} 的基础上依次排列）。
     */
    public static int controlX(int rowX, int rowWidth, int index, int... controlWidths) {
        int x = controlsX(rowX, rowWidth, controlsWidth(controlWidths));
        for (int i = 0; i < index; i++) x += controlWidths[i] + CONTROL_GAP;
        return x;
    }

    /**
     * 行内左侧文字的可用宽度：让开右侧控件，否则长标签会盖到按钮上。
     *
     * <p>控件整体宽度超过行宽时返回 0（宁可把文字截没，也不让它压住按钮）。</p>
     */
    public static int textRoom(int rowWidth, int controlsTotalWidth) {
        if (controlsTotalWidth <= 0) return Math.max(0, rowWidth - ROW_PADDING * 2);
        int room = rowWidth - ROW_PADDING - controlsTotalWidth - CONTROL_GAP - ROW_PADDING;
        return Math.max(0, room);
    }

    /** 底部按钮排成一行的起始 x（整排居中；装不下时贴左）。 */
    public static int footerButtonsX(int screenWidth, int... buttonWidths) {
        return Math.max(ROW_PADDING, screenWidth / 2 - controlsWidth(buttonWidths) / 2);
    }

    /** 一行里塞两行文字（标签 + 提示）需要的最小行高。 */
    public static int minRowHeightForTwoLines() {
        return HINT_OFFSET_Y + LINE_HEIGHT;
    }
}
