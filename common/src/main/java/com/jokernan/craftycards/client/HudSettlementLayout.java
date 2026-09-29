package com.jokernan.craftycards.client;

/**
 * 结算界面的竖排布局（纯算术、可单测）。
 *
 * <p>此前这里是一串手调的魔法偏移：三家共用一条 52px 的"基础行高"，第二、三家还额外上移
 * 1.5 倍行高——结果是三家的上下顺序被打乱成"第 1、0、2 家"，而且第 1 家的牌行正好压在
 * 押注信息上。改成"每行只负责自己的高度、依次往下走"：给定各处高度后，重叠在算术上就不
 * 可能发生，并由 {@code HudSettlementLayoutTest} 把这条性质固定住。
 *
 * <p>屏幕太矮时先压缩家与家之间的间距（下限 {@link #MIN_ROW_GAP}），保证整体放得下、
 * 且底部"点击任意处关闭"那一行始终不被挤到屏外。
 */
final class HudSettlementLayout {
    /** 结果行与下方之间的留白。 */
    private static final int GAP_AFTER_HEADER = 9;
    /** 押注信息与第一家之间的留白。 */
    private static final int GAP_AFTER_BET = 8;
    /** 同一家里"名字"到牌行之间的留白。 */
    private static final int LABEL_TO_CARDS = 4;
    /** 家与家之间的默认间距。 */
    private static final int ROW_GAP = 8;
    /** 家与家之间的最小间距（屏幕矮时压缩到此值）。 */
    private static final int MIN_ROW_GAP = 2;
    /** 顶部最小留白。 */
    private static final int TOP_MARGIN = 6;
    /** 底部为"点击任意处关闭"预留的高度。 */
    private static final int CLOSE_RESERVE = 18;

    /** 结果行 y（如"地主 X 获胜！"）。 */
    int resultY;
    /** 押注信息行 y；-1 表示不显示。 */
    int betY;
    /** 输赢分配行 y；-1 表示不显示。 */
    int betWinY;
    /** 三家"剩余 N 张"文字的 y，下标 = 座位索引。 */
    final int[] nameY = new int[3];
    /** 三家牌行的竖直中心 y，下标 = 座位索引（{@code renderCardsRow} 的 centerY）。 */
    final int[] cardsCenterY = new int[3];
    /** 底部"点击任意处关闭" y。 */
    int closeY;

    /** 字段由 {@link #solve} 逐项填好后返回，构造后不再改动。 */
    private HudSettlementLayout() {}

    /**
     * 解算布局。
     *
     * @param screenHeight  屏幕 GUI 高度
     * @param lineHeight    字体行高（{@code font.lineHeight}）
     * @param cardRowHeight 牌行渲染高度（像素，见 {@link RenderConfig#hudSmallCardH()}）
     * @param betLines      押注信息行数：0 = 无押注，1 = 只有押注，2 = 押注 + 输赢分配
     */
    static HudSettlementLayout solve(int screenHeight, int lineHeight, int cardRowHeight, int betLines) {
        int headerH = lineHeight + GAP_AFTER_HEADER;
        int betH = betLines > 0 ? betLines * lineHeight + GAP_AFTER_BET : 0;
        int rowBlockH = lineHeight + LABEL_TO_CARDS + cardRowHeight;
        int avail = screenHeight - CLOSE_RESERVE;

        // 屏幕矮时先收家与家的间距，宁可挨紧也不要重叠或把底部提示挤出屏
        int rowGap = ROW_GAP;
        while (rowGap > MIN_ROW_GAP && headerH + betH + rowBlockH * 3 + rowGap * 2 > avail - TOP_MARGIN) {
            rowGap--;
        }

        int total = headerH + betH + rowBlockH * 3 + rowGap * 2;
        int y = Math.max(TOP_MARGIN, (avail - total) / 2);

        HudSettlementLayout l = new HudSettlementLayout();
        l.resultY = y;
        y += headerH;

        l.betY = betLines >= 1 ? y : -1;
        if (betLines >= 1) y += lineHeight;
        l.betWinY = betLines >= 2 ? y : -1;
        if (betLines >= 2) y += lineHeight;
        if (betLines > 0) y += GAP_AFTER_BET;

        for (int i = 0; i < 3; i++) {
            l.nameY[i] = y;
            y += lineHeight + LABEL_TO_CARDS;
            l.cardsCenterY[i] = y + cardRowHeight / 2;
            y += cardRowHeight;
            if (i < 2) y += rowGap;
        }

        // 底部提示：正常贴在屏底；若内容很长就退到内容下方，但绝不越出屏幕
        l.closeY = Math.min(Math.max(y + GAP_AFTER_BET, screenHeight - CLOSE_RESERVE), screenHeight - lineHeight - 2);
        return l;
    }
}
