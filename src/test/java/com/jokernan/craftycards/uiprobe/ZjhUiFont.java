package com.jokernan.craftycards.uiprobe;

/**
 * 近似的 Minecraft 默认字体字宽模型（纯算术，不需要游戏实例）。
 *
 * <p>布局检查必须知道"这行字有多宽"，但真字体只有游戏里才有（{@code Font.width}）。
 * 探针不启动游戏，所以这里按原版默认字体的<b>字符步进</b>做模型：
 * CJK 全角 9px、拉丁/数字 6px、空格 4px。原版 {@code Font.width} 对这三种字符正是这个量级，
 * 误差不超过 1px/字符——用于"框够不够宽"的预算判断足够，但<b>不</b>用于像素级断言。</p>
 *
 * <p>绘制侧真正的截断发生在 {@code ZJHLayout} 之后：{@code drawActions} 用
 * {@code font.plainSubstrByWidth(label, option.width()-2)} 把文字裁到框内。
 * 因此"标签完整可见"的判据是 {@code width(label) <= option.width()-2}。</p>
 */
final class ZjhUiFont {
    /** 原版字体行高（{@code font.lineHeight}），一行文字的竖直占位。 */
    static final int LINE_HEIGHT = 9;
    static final int SPACE = 4;
    static final int NARROW = 6;
    static final int WIDE = 9;

    private ZjhUiFont() {}

    /** 文本渲染宽度（近似原版 {@code Font.width}）。 */
    static int width(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') total += SPACE;
            else if (isWide(cp)) total += WIDE;
            else total += NARROW;
        }
        return total;
    }

    /** 全角判定：CJK 汉字、假名、全角标点、以及「·」这类原版按 4px 画的间隔号单列处理。 */
    static boolean isWide(int cp) {
        if (cp == 0x00B7) return false;                       // 「·」间隔号，原版 4px 档
        return (cp >= 0x2E80 && cp <= 0x9FFF)                 // CJK 部首/汉字/假名
            || (cp >= 0xF900 && cp <= 0xFAFF)                 // CJK 兼容汉字
            || (cp >= 0xFE30 && cp <= 0xFE4F)                 // CJK 兼容形式
            || (cp >= 0xFF00 && cp <= 0xFF60)                 // 全角形式
            || (cp >= 0xFFE0 && cp <= 0xFFE6);
    }

    /** 标签在给定框宽里是否完整显示（绘制侧会把文字裁到 {@code boxWidth-2}）。 */
    static boolean fits(String label, int boxWidth) {
        return width(label) <= boxWidth - 2;
    }

    /** 给定框宽能显示到第几个字符（模拟 {@code plainSubstrByWidth}，用于报告截断情况）。 */
    static String truncatedTo(String label, int boxWidth) {
        int limit = boxWidth - 2;
        int used = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < label.length(); ) {
            int cp = label.codePointAt(i);
            int advance = cp == ' ' ? SPACE : isWide(cp) ? WIDE : NARROW;
            if (used + advance > limit) break;
            used += advance;
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
