package com.jokernan.craftycards.uiprobe;

import com.jokernan.craftycards.client.HudOptionBar;
import com.jokernan.craftycards.client.ZJHLayout;
import com.jokernan.craftycards.client.CardGameHudLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 布局检查引擎：对"某阶段 × 某分辨率"算出全部几何量，再逐条判性质。
 *
 * <p>所有断言都只读 {@link ZJHLayout}、{@link HudOptionBar} 与 {@link ZjhUiFont} 的<b>真实</b>计算结果，
 * 不复制任何布局常数——布局改了，这里跟着变，报告里的数字就是界面上的数字。</p>
 *
 * <p>严重程度分两档：{@link Severity#ERROR} 必须修（任何分辨率下都是缺陷）；
 * {@link Severity#WARN} 是极端窄屏下的退化（例如 320×180 + 6 人时目标名字被截断），
 * 记在报告里但不判失败。</p>
 */
final class ZjhUiChecks {

    enum Severity { ERROR, WARN }

    record Finding(String stage, String viewport, String rule, Severity severity, boolean ok, String detail) {
        String verdict() {
            return ok ? "PASS" : severity == Severity.ERROR ? "FAIL" : "WARN";
        }

        @Override
        public String toString() {
            return verdict() + " · " + rule + " · " + detail;
        }
    }

    /** 结算页获胜行相对内容块顶部的偏移（与 {@code ZJHClient.result} 一致）。 */
    private static final int SETTLED_WINNER_OFFSET = 14;
    /** 反馈行（y=46 那一行）与提示行之间要留的余量。 */
    private static final int HEADER_MESSAGE_Y = 46;

    private final int width;
    private final int height;
    private final String stageId;
    private final String viewport;
    private final List<Finding> out = new ArrayList<>();

    private ZjhUiChecks(int width, int height, String stageId) {
        var canvas = CardGameHudLayout.viewport(width,height);
        this.width = canvas.width();
        this.height = canvas.height();
        this.stageId = stageId;
        this.viewport = width + "×" + height;
    }

    static List<Finding> run(int width, int height, ZjhUiStage stage) {
        int previousMargin=ZJHLayout.bottomMargin();
        ZJHLayout.setBottomMargin(CardGameHudLayout.bottomMargin(ZJHLayout.DEFAULT_BOTTOM_MARGIN,
            CardGameHudLayout.viewport(width,height).scale()));
        try {
        ZjhUiChecks c = new ZjhUiChecks(width, height, stage.id());
        if (stage.kind() == ZjhUiStage.Kind.SETTLED) {
            c.checkSettlement(stage);
        } else {
            c.checkBands(stage);
            c.checkCards();
            c.checkOptionRow(stage);
        }
        return List.copyOf(c.out);
        } finally {ZJHLayout.setBottomMargin(previousMargin);}
    }

    // ------------------------------------------------------------------ 竖向节奏

    /**
     * 竖向从下往上：卡牌（贴底）→ 手牌信息 → 选项行 → 提示行 → 反馈行。
     * 这是"选项在卡牌上方、卡牌永远最靠下"这条要求的可执行版本，
     * 也是与斗地主共用的一套语法（提示在选项上方、反馈在提示上方）。
     */
    private void checkBands(ZjhUiStage stage) {
        ZJHLayout.Cards cards = ZJHLayout.hand(width, height);
        int hintY = ZJHLayout.hintY(width, height);
        int optionsY = ZJHLayout.optionsY(width, height);
        int infoY = ZJHLayout.handInfoY(width, height);
        int messageY = ZJHLayout.messageY(width, height);
        expect("actions-clear-crosshair", optionsY+HudOptionBar.ITEM_HEIGHT<height/2-7,
            "操作区下沿="+(optionsY+HudOptionBar.ITEM_HEIGHT)+"，准星安全区上沿="+(height/2-7));

        // 反馈行让开顶部三行（标题 4 / 阶段 16 / 局势 30）
        expect("message-clears-header", messageY >= 30 + ZjhUiFont.LINE_HEIGHT + 4,
            "反馈行 y=" + messageY + "，需 ≥ " + (30 + ZjhUiFont.LINE_HEIGHT + 4));
        // 反馈行 → 提示行 → 选项行：统一间距，且顺序不能反
        expect("message-above-hint", messageY + ZjhUiFont.LINE_HEIGHT <= hintY,
            "反馈行 " + messageY + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ 提示行 " + hintY);
        expect("message-gap-shared", optionsY - hintY == HudOptionBar.HINT_GAP
                && hintY - messageY == HudOptionBar.MESSAGE_GAP,
            "间距 提示−反馈=" + (hintY - messageY) + "、选项−提示=" + (optionsY - hintY)
                + "，应等于 HudOptionBar 的 " + HudOptionBar.MESSAGE_GAP + "/" + HudOptionBar.HINT_GAP);

        if (stage.hasOptionRow()) {
            expect("hint-above-options", hintY + ZjhUiFont.LINE_HEIGHT <= optionsY,
                "提示行 " + hintY + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ 选项行 " + optionsY);
            expect("options-above-hand-info", optionsY + HudOptionBar.ITEM_HEIGHT <= infoY,
                "选项行 " + optionsY + "+" + HudOptionBar.ITEM_HEIGHT + " ≤ 手牌信息 " + infoY);
        } else {
            expect("hint-above-hand-info", hintY + ZjhUiFont.LINE_HEIGHT <= infoY,
                "提示行 " + hintY + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ 手牌信息 " + infoY + "（该阶段无选项行）");
        }
        expect("hand-info-above-cards", infoY + ZjhUiFont.LINE_HEIGHT <= cards.top(),
            "手牌信息 " + infoY + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ 卡牌上沿 " + cards.top());
    }

    // ------------------------------------------------------------------ 卡牌（排面）

    private void checkCards() {
        ZJHLayout.Cards l = ZJHLayout.hand(width, height);

        // 用户明确要求：卡牌永远贴在最下面
        expect("cards-bottom-anchored", l.bottom() == height - ZJHLayout.bottomMargin(),
            "卡牌下沿 " + l.bottom() + " == 屏高 " + height + " − 底边距 " + ZJHLayout.bottomMargin()
                + "（底边距默认值与斗地主 RenderConfig.hudBottomMargin 一致，客户端每帧同步）");

        // CardVisuals 按 width*16/10.2 出图，布局高度必须与之一致，否则选中框/绿框套不住牌
        double drawn = CardGameHudLayout.cardScale(l.width());
        expect("card-model-bounds", Math.abs(l.height() - drawn) <= 0.5,
            "布局高 " + l.height() + " vs 实绘 " + String.format("%.1f", drawn));

        expect("cards-do-not-overlap", l.gap() > l.width(),
            "间距 " + l.gap() + " > 牌宽 " + l.width());

        expect("cards-inside-viewport", l.left() >= 8 && l.centerX(2) + l.width() / 2 <= width - 8,
            "左 " + l.left() + " / 右 " + (l.centerX(2) + l.width() / 2) + "，视口宽 " + width);

        int groupCenter2 = l.left() + (l.centerX(2) + l.width() / 2);
        expect("cards-centered", Math.abs(groupCenter2 - width) <= 2,
            "三张牌整体中心 " + String.format("%.1f", groupCenter2 / 2.0) + " vs 屏心 " + (width / 2.0));

        expect("cards-fit-height", l.top() >= 60,
            "卡牌上沿 " + l.top() + "，需 ≥ 60（让开标题/阶段/局势/反馈四行）");
    }

    // ------------------------------------------------------------------ 选项行

    private void checkOptionRow(ZjhUiStage stage) {
        if (!stage.hasOptionRow()) return;
        List<String> labels = stage.options();
        int[] widths = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) widths[i] = ZjhUiFont.width(labels.get(i));

        int y = ZJHLayout.optionsY(width, height);
        HudOptionBar.Item[] items = HudOptionBar.solve(width, widths, y);

        expect("option-count", items.length == labels.size(),
            "排布 " + items.length + " 项 vs 阶段 " + labels.size() + " 项");

        // 焦点高亮框比选项左右各宽 FOCUS_PAD，边界按它判
        expect("option-inside-viewport",
            items.length > 0 && items[0].focusLeft() >= 0 && items[items.length - 1].focusRight() <= width,
            items.length == 0 ? "空" : "左 " + items[0].focusLeft() + " / 右 "
                + items[items.length - 1].focusRight() + "，视口宽 " + width);

        // 等宽是这套语言的前提：与斗地主叫分选项同一个排版
        boolean uniform = true;
        for (HudOptionBar.Item item : items) if (item.width() != items[0].width()) uniform = false;
        expect("option-uniform-width", uniform,
            "各项宽 " + (items.length == 0 ? "-" : items[0].width() + "px × " + items.length));

        boolean overlap = false;
        int minWidth = Integer.MAX_VALUE;
        for (int i = 0; i < items.length; i++) {
            minWidth = Math.min(minWidth, items[i].width());
            if (i > 0 && items[i - 1].right() > items[i].x()) overlap = true;
        }
        expect("option-no-overlap", !overlap, overlap ? "相邻选项有重叠" : "共 " + items.length + " 项互不重叠");
        expect("option-min-hit-width", items.length == 0 || minWidth >= 10,
            "最窄一项 " + (items.length == 0 ? 0 : minWidth) + "px（点击区下限 10）");

        // 文字必须完整显示：绘制侧按 item.textLimit() 截断
        StringBuilder cut = new StringBuilder();
        for (int i = 0; i < items.length && i < labels.size(); i++) {
            if (!ZjhUiFont.fits(labels.get(i), items[i].width())) {
                if (cut.length() > 0) cut.append("；");
                cut.append('「').append(labels.get(i)).append("」只剩「")
                    .append(ZjhUiFont.truncatedTo(labels.get(i), items[i].width())).append('」');
            }
        }
        boolean truncated = cut.length() > 0;
        // 窄屏 + 大量长标签（6 人局的目标名字）截断属可接受的退化，只告警
        Severity severity = width >= 640 ? Severity.ERROR : Severity.WARN;
        expect("option-label-fully-visible", !truncated, severity,
            truncated ? cut.toString() : labels.size() + " 个标签全部完整显示");
    }

    // ------------------------------------------------------------------ 结算页

    private void checkSettlement(ZjhUiStage stage) {
        int players = stage.players();
        int row = ZJHLayout.resultRowHeight(height, players);
        int top = ZJHLayout.resultTop(height, players);
        int firstRow = top + ZJHLayout.RESULT_HEADER_H;
        int hintY = ZJHLayout.resultHintY(height);

        expect("settled-header-above-rows",
            top + SETTLED_WINNER_OFFSET + ZjhUiFont.LINE_HEIGHT <= firstRow,
            "获胜行 " + (top + SETTLED_WINNER_OFFSET) + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ 第一行 " + firstRow);

        // 与斗地主一致：内容块垂直居中，不能贴顶留一大片空白（两人局 960×540 曾只在顶部占 118px）
        expect("settled-block-centered", top >= ZJHLayout.RESULT_TOP_MARGIN
                && (height < 360 || top > ZJHLayout.RESULT_TOP_MARGIN),
            "内容块起始 y=" + top + "（上边界 " + ZJHLayout.RESULT_TOP_MARGIN + "），屏高 " + height);

        // 结算页没有选项行：底部只有一行"右键任意位置关闭"
        int lastRowBottom = firstRow + players * row - 2;
        expect("settled-rows-clear-hint", lastRowBottom <= hintY - 6,
            players + " 行 × " + row + " → 末行下沿 " + lastRowBottom + " ≤ 底部提示 " + (hintY - 6));

        // 行内文字：名字一行恒在行内（y+row/2-4，需 row≥14）
        expect("settled-row-fits-name", row >= 14,
            "行高 " + row + " ≥ 14（名字行 y+row/2−4 不越界）");

        // 右侧文字列：不能压到牌列，且要留出可读宽度
        int cardW = Math.max(7, Math.min(23, (int)((row-2)*CardGameHudLayout.MODEL_WIDTH/16)));
        int cardColumnEnd = width / 3 + 3 * (cardW + 3);
        int textX = Math.max(cardColumnEnd + 6, width * 2 / 3);
        expect("settled-text-column-clear", textX > cardColumnEnd && width - textX - 10 >= 40,
            "牌列到 " + cardColumnEnd + "，文字列自 " + textX + " 起，可用宽 " + (width - textX - 10));

        // 两行文字只在行高够时才叠；不够时渲染侧必须退成一行
        if (ZJHLayout.resultRowFitsTwoLines(row)) {
            int line1 = row / 2 - 9, line2 = row / 2 + 1;
            expect("settled-two-lines-inside-row", line1 >= 0 && line2 + ZjhUiFont.LINE_HEIGHT <= row - 2,
                "行高 " + row + "：第一行 y+" + line1 + "、第二行 y+" + line2 + "+" + ZjhUiFont.LINE_HEIGHT
                    + " 都在 0.." + (row - 2) + " 内");
        } else {
            int line = row / 2 - 4;
            expect("settled-one-line-inside-row", line >= 0 && line + ZjhUiFont.LINE_HEIGHT <= row - 2,
                "行高 " + row + " < 22 → 退成一行：y+" + line + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ " + (row - 2));
            // 一行模式会先省掉"净得失"前缀、再裁牌型；但净得失的数字必须留得住
            int tail = ZjhUiFont.width(" -9999");
            expect("settled-tight-net-visible", width - textX - 10 >= tail + ZjhUiFont.WIDE,
                "文字列可用宽 " + (width - textX - 10) + " ≥ 数字 " + tail + " + 一个牌型字 " + ZjhUiFont.WIDE);
        }

        expect("settled-hint-inside-viewport", hintY + ZjhUiFont.LINE_HEIGHT <= height,
            "提示行 " + hintY + "+" + ZjhUiFont.LINE_HEIGHT + " ≤ " + height);
        expect("settled-title-inside-viewport", top >= 0 && top + ZjhUiFont.LINE_HEIGHT <= height,
            "标题行 " + top + " 在视口内");
    }

    // ------------------------------------------------------------------ 记账

    private void expect(String rule, boolean ok, String detail) {
        expect(rule, ok, Severity.ERROR, detail);
    }

    private void expect(String rule, boolean ok, Severity severity, String detail) {
        out.add(new Finding(stageId, viewport, rule, severity, ok, detail));
    }
}
