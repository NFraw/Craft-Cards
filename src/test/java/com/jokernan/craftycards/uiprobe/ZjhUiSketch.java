package com.jokernan.craftycards.uiprobe;

import com.jokernan.craftycards.client.HudOptionBar;
import com.jokernan.craftycards.client.ZJHLayout;
import com.jokernan.craftycards.client.CardGameHudLayout;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 把"某阶段 × 某分辨率"的布局<b>画成一张线框图</b>（PNG，纯 Java2D，不需要游戏）。
 *
 * <p>这不是截图，是<b>设计稿</b>：所有坐标都来自 {@link ZJHLayout} 的真实计算，
 * 颜色取自 {@code ZJHClient} 的调色板，因此"图上哪里空、哪里挤"与游戏里是一致的，
 * 差别只在字体（这里用系统字体，游戏用原版字体）与卡牌贴图（这里画成带点数的白框）。</p>
 *
 * <p>用途是让布局检查的结果<b>可看</b>：报告里说"320×180 下比牌选目标被截断"，
 * 图上一眼就能看到是哪几项被裁了。</p>
 */
final class ZjhUiSketch {

    // 与 ZJHClient 同一套调色板（ARGB）
    private static final Color TITLE = new Color(0xFFFFFFFF, true);
    private static final Color PHASE = new Color(0xFFAAAAAA, true);
    private static final Color JOIN = new Color(0xFFFFAA00, true);
    private static final Color CHIP = new Color(0xFF55FF55, true);
    private static final Color HINT = new Color(0xFF55FF55, true);
    private static final Color MESSAGE = new Color(0xFFFF5555, true);
    private static final Color FOCUS = new Color(0xFFFFD700, true);
    private static final Color OPTION = new Color(0xFFFFFFFF, true);
    private static final Color HAND_INFO = new Color(0xFFDDDDDD, true);
    private static final Color GOLD = new Color(0xFFFFD700, true);
    private static final Color SELF = new Color(0xFF7FD4FF, true);
    private static final Color OTHER = new Color(0xFFCCCCCC, true);
    private static final Color SCRIM = new Color(0xA0000000, true);

    /** 画布放大倍数：布局按 1× 计算，出图放大到 2× 便于人眼核对。 */
    private static final int SCALE = 2;
    /** 顶部说明条高度（像素，不参与布局）。 */
    private static final int CAPTION = 20;

    private static String family;
    private static String symbolFamily;

    private ZjhUiSketch() {}

    static void render(Path out, ZjhUiStage stage, int w, int h, List<ZjhUiChecks.Finding> findings) throws IOException {
        BufferedImage image = new BufferedImage(w * SCALE, h * SCALE + CAPTION, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // ---- 说明条
        g.setColor(new Color(0xFF1E1E1E, true));
        g.fillRect(0, 0, w * SCALE, CAPTION);
        g.setColor(Color.WHITE);
        g.setFont(font(11));
        long fails = findings.stream().filter(f -> !f.ok() && f.severity() == ZjhUiChecks.Severity.ERROR).count();
        long warns = findings.stream().filter(f -> !f.ok() && f.severity() == ZjhUiChecks.Severity.WARN).count();
        String caption = stage.id() + "  ·  " + stage.title() + "  ·  " + w + "×" + h
            + (fails + warns == 0 ? "  ·  全部通过" : "  ·  FAIL " + fails + " / WARN " + warns);
        g.drawString(caption, 6, 14);

        // ---- HUD 区（1× 坐标，整体下移 CAPTION 后放大）
        g.translate(0, CAPTION);
        g.scale(SCALE, SCALE);
        var canvas=CardGameHudLayout.viewport(w,h);
        int previousMargin=ZJHLayout.bottomMargin();
        ZJHLayout.setBottomMargin(CardGameHudLayout.bottomMargin(ZJHLayout.DEFAULT_BOTTOM_MARGIN,canvas.scale()));
        g.scale(canvas.scale(),canvas.scale());
        w=canvas.width();h=canvas.height();
        paintWorld(g, w, h);
        if (stage.kind() == ZjhUiStage.Kind.SETTLED) {
            g.setColor(SCRIM);
            g.fillRect(0, 0, w, h);
            paintSettlement(g, stage, w, h);
        } else {
            paintTopRows(g, stage, w, h);
            paintHint(g, stage, w, h);
            if (stage.hasOptionRow()) paintOptions(g, stage, w, h);
            if (stage.kind() != ZjhUiStage.Kind.WAITING) paintHand(g, stage, w, h);
        }
        g.dispose();
        ZJHLayout.setBottomMargin(previousMargin);
        ImageIO.write(image, "png", out.toFile());
    }

    /** 背景：模拟游戏画面（上半天空、下半地面），让半透明遮罩/文字的对比度可判断。 */
    private static void paintWorld(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(0xFF6FA8DC), 0, h, new Color(0xFF3B5A34)));
        g.fillRect(0, 0, w, h);
    }

    private static void paintTopRows(Graphics2D g, ZjhUiStage stage, int w, int h) {
        centered(g, "炸金花", w / 2, 4, TITLE);
        if (stage.kind() == ZjhUiStage.Kind.WAITING) {
            centered(g, "加入即就绪 · 人数足够自动开局", w / 2, 16, PHASE);
            centered(g, "等待玩家加入 (" + stage.players() + "/3)", w / 2, 30, JOIN);
            centered(g, "本桌只记虚拟分数", w / 2, 46, CHIP);
        } else {
            centered(g, "第 3 轮 | 轮到你行动", w / 2, 16, PHASE);
            centered(g, "锅底 12 | 暗注 1 | 上限 200", w / 2, 30, PHASE);
        }
    }

    private static void paintHint(Graphics2D g, ZjhUiStage stage, int w, int h) {
        String hint = switch (stage.kind()) {
            case SELECT_CARDS -> "选牌模式 · 滚轮定位 · 左键选牌 · 右键牌桌翻牌 · 右键空气返回";
            case PICK_TARGET -> "滚轮选择比牌目标 | 右键牌桌确认 | 右键空气返回";
            case WAITING -> "滚轮选择 | 右键牌桌确认 | Shift+右键离开";
            default -> "滚轮选择 | 右键牌桌确认";
        };
        centered(g, hint, w / 2, ZJHLayout.hintY(w, h), HINT);
    }

    /** 一行选项：等宽项 + 项内居中，与斗地主叫分选项同一个 {@link HudOptionBar}。 */
    private static void paintOptions(Graphics2D g, ZjhUiStage stage, int w, int h) {
        List<String> labels = stage.options();
        int[] widths = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) widths[i] = ZjhUiFont.width(labels.get(i));
        HudOptionBar.Item[] items = HudOptionBar.solve(w, widths, ZJHLayout.optionsY(w, h));
        // 焦点取最后一项：与"滚轮默认停在最后一项"同一约定
        int focus = items.length - 1;
        for (int i = 0; i < items.length && i < labels.size(); i++) {
            HudOptionBar.Item item = items[i];
            if (i == focus) {
                g.setColor(new Color(HudOptionBar.COLOR_FOCUS_BACK, true));
                g.fillRect(item.focusLeft(), item.focusTop(),
                    item.focusRight() - item.focusLeft(), item.focusBottom() - item.focusTop());
            }
            drawCenteredClipped(g, labels.get(i), item.centerX(), item.y(), item.textLimit(),
                i == focus ? FOCUS : OPTION);
        }
    }

    private static void paintHand(Graphics2D g, ZjhUiStage stage, int w, int h) {
        ZJHLayout.Cards l = ZJHLayout.hand(w, h);
        int[] ids = {0, 17, 34};   // A♠ / 6♥ / 9♣，仅用于示意
        for (int i = 0; i < 3; i++) {
            boolean shown = i < stage.revealed();
            paintCard(g, l.centerX(i), l.centerY(), l.width(), l.height(), shown ? ids[i] : -1);
        }
        String info = (stage.revealed() == 0 ? "暗牌" : "明牌 " + stage.revealed() + "/3")
            + " | 投入 4 | 余额 196";
        centered(g, info, w / 2, ZJHLayout.handInfoY(w, h), HAND_INFO);
    }

    private static void paintSettlement(Graphics2D g, ZjhUiStage stage, int w, int h) {
        int players = stage.players();
        int row = ZJHLayout.resultRowHeight(h, players);
        int top = ZJHLayout.resultTop(h, players);
        centered(g, "本局结算", w / 2, top, GOLD);
        centered(g, "Player1 获胜", w / 2, top + 14, GOLD);

        int cardW = Math.max(7, Math.min(23, (int)((row-2)*CardGameHudLayout.MODEL_WIDTH/16)));
        String[] types = {"对子", "金花", "顺子", "散牌", "豹子", "特殊235"};
        for (int i = 0; i < players; i++) {
            int y = top + ZJHLayout.RESULT_HEADER_H + i * row;
            boolean winner = i == 0;
            Color color = winner ? GOLD : i == 1 ? SELF : OTHER;
            g.setColor(new Color(winner ? 0x554A3B0C : 0x33333333, true));
            g.fillRect(10, y, w - 10, row - 2);
            drawClipped(g, "Player" + (i + 1), 14, y + row / 2 - 4, Math.max(24, w / 4 - 10), color);
            for (int c = 0; c < 3; c++) {
                paintCard(g, w / 3 + c * (cardW + 3) + cardW / 2, y + row / 2, cardW, CardGameHudLayout.cardHeight(cardW), c * 4 + i);
            }
            int x = Math.max(w / 3 + 3 * (cardW + 3) + 6, w * 2 / 3);
            String headline = (winner ? "胜 " : "出局 ") + types[i % types.length];
            String net = "净得失 " + (winner ? "+" : "−") + (12 + i * 3);
            String signed = (winner ? "+" : "−") + (12 + i * 3);
            if (ZJHLayout.resultRowFitsTwoLines(row)) {
                drawClipped(g, headline, x, y + row / 2 - 9, w - x - 10, color);
                drawClipped(g, net, x, y + row / 2 + 1, w - x - 10, color);
            } else {
                // 与 ZJHClient 同一取舍：一行时省掉"净得失"前缀，牌型可裁、数字必须留
                String tail = " " + signed;
                int room = w - x - 10;
                String head = ZjhUiFont.truncatedTo(headline, room - ZjhUiFont.width(tail));
                drawClipped(g, head + tail, x, y + row / 2 - 4, room, color);
            }
        }

        // 结算页没有选项行：底部只有一行"右键任意位置关闭"
        centered(g, "已自动结算并退局 · 右键任意位置关闭", w / 2, ZJHLayout.resultHintY(h), HINT);
    }

    /** 一张牌：白底 + 圆角 + 点数花色；{@code id<0} 画成牌背。 */
    private static void paintCard(Graphics2D g, int cx, int cy, int cw, int ch, int id) {
        int x = cx - cw / 2, y = cy - ch / 2;
        g.setColor(id < 0 ? new Color(0xFF1B3A6B) : new Color(0xFFF7F7F2));
        g.fillRoundRect(x, y, cw, ch, 3, 3);
        g.setColor(new Color(0x66000000, true));
        g.drawRoundRect(x, y, cw, ch, 3, 3);
        if (id < 0) return;
        String rank = switch (id / 4 + 1) {
            case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K";
            default -> String.valueOf(id / 4 + 1);
        };
        String[] suits = {"♠", "♥", "♣", "♦"};
        String text = rank + suits[id % 4];
        // 花色符号（♠♥♣♦）在雅黑里是缺字（画出来是豆腐块），得换一个含符号的字体
        g.setFont(symbolFont(Math.max(6, Math.min(11, cw / 2))));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0xFF202020));
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 1);
    }

    // ---------------------------------------------------------------- 文字

    private static void centered(Graphics2D g, String text, int cx, int y, Color color) {
        g.setFont(font(9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(CardGameHudLayout.COLOR_TEXT_BACK,true));
        g.fillRect(cx-fm.stringWidth(text)/2-3,y-2,fm.stringWidth(text)+6,13);
        g.setColor(color);
        g.drawString(text, cx - fm.stringWidth(text) / 2, y + fm.getAscent() - 1);
    }

    /** 模拟 {@code plainSubstrByWidth}：把文字裁到 {@code limit} 像素内，不越出框。 */
    private static void drawClipped(Graphics2D g, String text, int x, int y, int limit, Color color) {
        g.setFont(font(9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(clip(g, text, limit), x, y + fm.getAscent() - 1);
    }

    /** 项内居中（等宽选项行的画法）：先按 {@code limit} 裁，再在 {@code cx} 上居中。 */
    private static void drawCenteredClipped(Graphics2D g, String text, int cx, int y, int limit, Color color) {
        g.setFont(font(9));
        FontMetrics fm = g.getFontMetrics();
        String clipped = clip(g, text, limit);
        g.setColor(color);
        g.drawString(clipped, cx - fm.stringWidth(clipped) / 2, y + fm.getAscent() - 1);
    }

    private static String clip(Graphics2D g, String text, int limit) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int advance = fm.stringWidth(ch);
            if (used + advance > limit) break;
            used += advance;
            sb.append(ch);
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static Font font(int size) {
        return new Font(family(), Font.PLAIN, size);
    }

    /** 含 ♠♥♣♦ 的字体（雅黑/宋体都没有这四个符号，直接用会画成豆腐块）。 */
    private static Font symbolFont(int size) {
        if (symbolFamily == null) {
            symbolFamily = Font.DIALOG;
            for (String candidate : new String[]{"Arial Unicode MS", "MS Gothic", "DejaVu Sans", "Segoe UI Symbol"}) {
                if (new Font(candidate, Font.PLAIN, 12).canDisplay('♠')) {
                    symbolFamily = candidate;
                    break;
                }
            }
        }
        return new Font(symbolFamily, Font.PLAIN, size);
    }

    /** 选一个能显示汉字的字体（Windows 上是雅黑；否则退回逻辑字体，由系统做回退）。 */
    private static String family() {
        if (family != null) return family;
        for (String candidate : new String[]{"Microsoft YaHei", "微软雅黑", "SimHei", "Noto Sans CJK SC", "SansSerif"}) {
            Font f = new Font(candidate, Font.PLAIN, 12);
            if (f.canDisplay('炸') && f.canDisplay('金')) {
                family = candidate;
                return family;
            }
        }
        family = Font.SANS_SERIF;
        return family;
    }
}
