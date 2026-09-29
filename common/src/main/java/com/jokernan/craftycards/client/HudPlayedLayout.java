package com.jokernan.craftycards.client;

/**
 * 「上轮出牌」HUD 块的布局解算：给定屏幕宽度、手牌区位置和要显示的牌数，
 * 算出标签与牌行各自的落点。纯算术、不引用任何 Minecraft 类型，故可直接单测。
 *
 * <p>布局意图：牌行贴在手牌右上角外侧一点点。<b>标签由调用方画在牌行正上方</b>（与底牌行同款），
 * 故调用方把 {@code labelWidth} 传 0这里只解算牌行本身，横向也不为标签预留宽度。
 * 保留 {@code labelX} 字段是为了兼容「标签在牌行左侧」的旧布局与既有单测。
 *
 * <p>不变量（见 {@code HudPlayedLayoutTest}）：
 * <ul>
 *   <li>整块（标签 + 牌行）始终落在左右边距内，任何窗口尺寸下都不出屏</li>
 *   <li>牌行底边始终高于手牌顶边，不压住手牌（含选中牌上移的高度）</li>
 * </ul>
 *
 * @param labelX     旧布局里标签文字左端；当前调用方把标签画在牌行上方，故不使用
 * @param rowLeft    牌行最左牌的左边缘
 * @param right      整块右边缘（牌行右端）
 * @param rowBottom  牌行底边 y（屏幕坐标，向下为正）
 * @param rowHeight  牌行渲染高度（像素）
 * @param gap        牌间距（牌多时会压缩，可能小于配置值）
 */
record HudPlayedLayout(int labelX, int rowLeft, int right, int rowBottom, int rowHeight, int gap) {

    /**
     * 布局参数。正常由调用方从 {@link RenderConfig} 取值构造；单测直接构造各种组合。
     *
     * @param cardW      单张牌渲染宽度（像素），牌行高度由它推导
     * @param cardGap    牌间距（牌少时按此排布）
     * @param minGap     牌间距下限（牌多时压缩到不低于此值，允许轻微叠压）
     * @param offsetX    相对手牌右缘再往右的偏移（首选的右对齐位置）
     * @param offsetY    牌行底边相对手牌顶边再往上的偏移（须大于选中牌上移量）
     * @param edgeMargin 距屏幕左右边缘的最小边距
     * @param labelGap   标签与牌行之间的间隙
     */
    record Config(int cardW, int cardGap, int minGap, int offsetX, int offsetY,
                  int edgeMargin, int labelGap) {}

    /**
     * 解算布局。
     *
     * @param screenWidth 屏幕 GUI 宽度（像素）
     * @param handEndX    手牌区右边缘 x
     * @param handTop     手牌区顶边 y
     * @param playedCount 上轮出牌的牌数（≥1）
     * @param labelWidth  标签文字宽度（"谁 出牌 · 牌型"）
     * @param c           布局参数
     */
    static HudPlayedLayout solve(int screenWidth, int handEndX, int handTop,
                                 int playedCount, int labelWidth, Config c) {
        int rowHeight = rowHeight(c.cardW());

        // 右对齐位置：优先贴手牌右缘外侧。整块在手牌上方、与手牌不冲突，
        // 故窗口窄/手牌少导致放不下时可以继续右移到屏内边距处，保证标签与牌行都在屏内
        int right = Math.min(handEndX + c.offsetX(), screenWidth - c.edgeMargin());
        int minRowW = playedCount > 1 ? c.minGap() * (playedCount - 1) + c.cardW() : c.cardW();
        int needed = c.edgeMargin() + labelWidth + c.labelGap() + minRowW;
        if (right < needed) right = Math.min(screenWidth - c.edgeMargin(), needed);

        // 牌行可用宽度 = 右侧可用宽 - 标签占位 - 标签与牌行间隙
        int maxRowW = right - c.edgeMargin() - labelWidth - c.labelGap();
        int gap = playedCount > 1
            ? Math.max(c.minGap(), Math.min(c.cardGap(), (maxRowW - c.cardW()) / (playedCount - 1)))
            : c.cardGap();
        int rowW = Math.max(c.cardW(), gap * (playedCount - 1) + c.cardW());
        int rowLeft = right - rowW;
        int labelX = rowLeft - c.labelGap() - labelWidth;

        int rowBottom = handTop - c.offsetY();
        return new HudPlayedLayout(labelX, rowLeft, right, rowBottom, rowHeight, gap);
    }

    /** 牌行渲染高度：卡牌模型长边烘焙后为 1.0 格，缩放后即像素高度。 */
    static int rowHeight(int cardW) {
        return Math.round(cardW * 16F / 10.2F);
    }

    /** 牌行竖直中心 y。 */
    int rowCenterY() {
        return rowBottom - rowHeight / 2;
    }
}
