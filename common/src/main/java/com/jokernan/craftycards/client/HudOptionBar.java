package com.jokernan.craftycards.client;

/**
 * 选项行：斗地主叫分、炸金花行动、以及两个游戏结算页底部操作的<b>同一套</b>排版与配色。
 *
 * <p>此前三个地方各写一份：斗地主是"固定 40px 一项、文字居中、焦点框右边界少 2px"，
 * 炸金花是"按文字宽度算、文字左对齐、焦点框右边界多 2px"。同一个玩家在两局游戏里
 * 看到的选项行是两种东西——项宽不等、对齐方式不同、高亮框宽度也不一样。
 * 这里把它们收成一份纯算术 + 一份配色常量，两边都从这里取。</p>
 *
 * <p><b>等宽</b>是这套语言的核心：所有项一样宽、文字在项内居中，看起来像一排标签页，
 * 而不是一串参差不齐的字。宽度取"最宽标签 + 两侧留白"，下限 {@link #MIN_ITEM_WIDTH}
 * （斗地主四个两字选项正好各 40px，所以斗地主的观感与从前完全一致）；
 * 窄屏放不下时按 {@code (视口宽-2*边距)/项数} 等分压缩，不越界、不重叠。</p>
 */
public final class HudOptionBar {
    /** 选项文字高度：也是深色底衬与焦点框的高度基准。 */
    public static final int ITEM_HEIGHT = 12;
    /** 单项最小宽度。斗地主「不叫 / 1分 / 2分 / 3分」各 40px，作为等宽项的下限。 */
    public static final int MIN_ITEM_WIDTH = 40;
    /** 文字两侧留白：等宽项里由居中吸收，只在"这一项需要多宽"的预算里用到。 */
    public static final int TEXT_PAD = 12;
    /** 选项行距屏幕左右边缘的最小留白。 */
    public static final int EDGE_MARGIN = 8;
    /** 焦点框相对选项四周的外扩量（左右各 2px，与斗地主原来的观感一致）。 */
    public static final int FOCUS_PAD = 2;
    /** 提示行在选项行<b>上方</b>多少像素。 */
    public static final int HINT_GAP = 13;
    /** 反馈行（红字）在提示行上方多少像素。 */
    public static final int MESSAGE_GAP = 13;
    /** 原版字体行高。 */
    public static final int TEXT_LINE_HEIGHT = 9;
    /** 顶部三行（标题 4 / 阶段 16 / 局势 30）之后，反馈行允许的最小 y。 */
    public static final int HEADER_SAFE_Y = 30 + TEXT_LINE_HEIGHT + 4;

    /**
     * 选项行允许的最小 y：再往上，反馈行就会压到顶部三行。
     *
     * <p>这条约束是被探针跑出来的：斗地主用固定偏移 {@code h-165} 定位操作块，
     * 在 320×180（1280×720 窗口 + GUI 缩放 4，原版允许的最小档）下算出 y=15——
     * 正好压在 y=16 的阶段行上，提示行更是落到 y=2 盖住标题。手牌几何与窗口高度无关的
     * 固定偏移，在矮屏上必然撞顶，所以要有一条下限兜住。</p>
     */
    public static int minOptionRowY() { return HEADER_SAFE_Y + HINT_GAP + MESSAGE_GAP; }

    /**
     * 操作块 y：距底固定偏移，但不得越过 {@link #minOptionRowY()}。
     *
     * @param height           视口高
     * @param offsetFromBottom 距屏幕底部的固定距离（斗地主是 {@code DDZGameHud.ACTION_ROW_OFFSET}）
     */
    public static int actionRowY(int height, int offsetFromBottom) {
        return Math.max(height - offsetFromBottom, minOptionRowY());
    }

    // ---- 配色：两个游戏共用，改这里就是两个一起改
    /** 普通项。 */
    public static final int COLOR_NORMAL = 0xFFFFFFFF;
    /** 当前焦点（滚轮选中）。 */
    public static final int COLOR_FOCUS = 0xFFFFD700;
    /** 不可选（服务端会拒绝）。 */
    public static final int COLOR_DISABLED = 0xFF666666;
    /** 焦点项的深色底衬。 */
    public static final int COLOR_FOCUS_BACK = 0x66000000;
    /** 操作提示（绿）。 */
    public static final int COLOR_HINT = 0xFF55FF55;
    /** 反馈/错误（红）。 */
    public static final int COLOR_MESSAGE = 0xFFFF5555;

    /**
     * 一项选项。{@code width} 对同一行里所有项都相同——等宽是这套语言的前提。
     */
    public record Item(int x, int y, int width) {
        public int right() { return x + width; }
        public int centerX() { return x + width / 2; }
        /** 焦点底衬左边界（含外扩）。 */
        public int focusLeft() { return x - FOCUS_PAD; }
        /** 焦点底衬右边界（含外扩）——与 {@link #focusLeft()} 对称。 */
        public int focusRight() { return right() + FOCUS_PAD; }
        public int focusTop() { return y - FOCUS_PAD; }
        /** 底衬下沿固定为 {@code y+10}（与斗地主原来的观感一致，比 {@link #ITEM_HEIGHT} 矮 2px）。 */
        public int focusBottom() { return y + 10; }
        /** 这一项能画的文字宽度上限（两侧各让 1px，避免压到相邻项）。 */
        public int textLimit() { return width - 2; }
    }

    private HudOptionBar() {}

    /**
     * 把若干选项排成一行等宽项，整体水平居中。
     *
     * @param viewportWidth 视口逻辑宽度
     * @param textWidths    每项文字的渲染宽度（用 {@code Font.width}）
     * @param y             选项行文字顶边 y
     * @return 与 {@code textWidths} 等长的项数组；没有选项时返回空数组
     */
    public static Item[] solve(int viewportWidth, int[] textWidths, int y) {
        int count = textWidths.length;
        if (count == 0) return new Item[0];

        int widest = 0;
        for (int w : textWidths) widest = Math.max(widest, w);
        int itemWidth = Math.max(MIN_ITEM_WIDTH, widest + TEXT_PAD);

        // 窄屏退让：等分可用宽度（每项至少 10px，否则点了也没意义）
        int available = Math.max(count * 10, viewportWidth - 2 * EDGE_MARGIN);
        if (itemWidth * count > available) itemWidth = Math.max(10, available / count);

        int total = itemWidth * count;
        int left = Math.max(EDGE_MARGIN, (viewportWidth - total) / 2);
        Item[] items = new Item[count];
        for (int i = 0; i < count; i++) items[i] = new Item(left + i * itemWidth, y, itemWidth);
        return items;
    }

    /** 提示行 y：永远在选项行<b>上方</b>（炸金花与斗地主出牌阶段本来就是这样，叫分阶段是反的，一并纠正）。 */
    public static int hintY(int optionsY) { return optionsY - HINT_GAP; }

    /** 反馈行 y：在提示行再往上一行，紧贴操作块——玩家操作时视线就在这一块，不该跑到屏幕顶部。 */
    public static int messageY(int optionsY) { return optionsY - HINT_GAP - MESSAGE_GAP; }

    /** 焦点项颜色；{@code enabled=false} 时置灰。 */
    public static int colorOf(boolean focused, boolean enabled) {
        if (!enabled) return COLOR_DISABLED;
        return focused ? COLOR_FOCUS : COLOR_NORMAL;
    }
}
