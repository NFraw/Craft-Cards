package com.jokernan.craftycards.client;

/**
 * HUD 手牌的横向布局解算（纯算术，可单测）。
 *
 * <p>此前的实现只按"牌的矩形宽度"分配空间，<b>没有算扇形旋转后牌角外扩的部分</b>：
 * 牌是绕自己的底边中心旋转的，倾角 θ 时顶角会向外甩出约
 * {@code 半宽·cosθ + 高·sinθ − 半宽}像素。手牌多、扇形角大时，最外侧那张的角就甩出屏幕
 * （表现为"最左边的牌看不到点数"）。这里把外扩量纳入预算，保证整片手牌的可视范围不越界。</p>
 */
final class HudHandMetrics {
    /**
     * 间距硬下限：连"首选下限"都放不下时继续压缩到此值。
     * 宁可让牌轻微重叠（仍能看出张数、点数露出上半部），也好过最外侧的牌跑出屏幕看不见。
     */
    static final int HARD_MIN_GAP = 8;

    private HudHandMetrics() {}

    record Viewport(int width, int height, float scale) {}

    static Viewport viewport(int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) return new Viewport(guiWidth, guiHeight, 1F);
        float scale = guiHeight / 320F;
        return new Viewport(Math.round(guiWidth / scale), Math.round(guiHeight / scale), scale);
    }

    /** GUI 模型的深度位置；独立于扇形角度、屏幕位置和选中上移。 */
    // GUI 正 z 朝向观察者。模型厚 0.5/16 且 z 不随牌宽缩放，1 足以隔开整张模型。
    // 选中只改变屏幕 y，不能改变覆盖顺序，否则会遮掉相邻牌的左上角牌号。
    static float cardDepth(int index) { return index; }

    static float focusDepth(int index) { return cardDepth(index) + 1F / 16F; }

    /**
     * @param screenWidth  屏幕 GUI 宽度
     * @param count        手牌张数
     * @param cardW        牌宽（像素）
     * @param cardH        牌高（像素）
     * @param handGap      默认牌间距
     * @param minGap       间距下限（张数多时压缩到不低于此值）
     * @param maxWidthRatio 手牌区最大占屏宽比
     * @param fanAngle     扇形倾角（度，最外侧牌的倾角）
     */
    static Layout solve(int screenWidth, int count, int cardW, int cardH,
                        int handGap, int minGap, float maxWidthRatio, float fanAngle) {
        if (count <= 0) return new Layout(handGap, screenWidth / 2, 0, 0);
        int overhang = fanOverhang(cardW, cardH, fanAngle);
        if (count == 1) {
            return new Layout(handGap, screenWidth / 2 - cardW / 2, cardW, overhang);
        }
        int maxTotal = (int) (screenWidth * maxWidthRatio);
        // 预留左右各一份扇形外扩量，否则最外侧的牌角会甩出屏幕
        int usable = maxTotal - cardW - overhang;
        int ideal = usable / (count - 1);
        int gap;
        if (ideal >= minGap) {
            gap = Math.min(handGap, ideal);          // 放得下，且不低于首选下限
        } else {
            gap = Math.max(HARD_MIN_GAP, ideal);     // 首选下限也放不下 → 压到硬下限（允许重叠）
        }
        int totalWidth = gap * (count - 1) + cardW;
        return new Layout(gap, screenWidth / 2 - totalWidth / 2, totalWidth, overhang);
    }

    /**
     * 扇形旋转带来的左右外扩总量（左右各一份）。
     *
     * <p>牌以底边中心为支点旋转 θ：原本横向半宽是 {@code w/2}，旋转后变为
     * {@code (w/2)·|cosθ| + h·|sinθ|}。多出来的就是外扩量，两端各一份。</p>
     */
    static int fanOverhang(int cardW, int cardH, float fanAngle) {
        double rad = Math.toRadians(Math.abs(fanAngle));
        if (rad <= 1e-6) return 0;
        double halfW = cardW / 2.0;
        double rotatedHalf = halfW * Math.abs(Math.cos(rad)) + cardH * Math.abs(Math.sin(rad));
        return (int) Math.ceil(2 * Math.max(0.0, rotatedHalf - halfW));
    }

    /**
     * @param gap        实际使用的牌间距
     * @param startX     第 0 张牌的左边缘 x
     * @param totalWidth 布局宽度（不含扇形外扩）
     * @param overhang   扇形旋转的左右外扩总量
     */
    record Layout(int gap, int startX, int totalWidth, int overhang) {
        /** 整片手牌的实际可视左边缘（含扇形外扩）。 */
        int visualLeft() {
            return startX - overhang / 2;
        }

        /** 整片手牌的实际可视右边缘（含扇形外扩）。 */
        int visualRight() {
            return startX + totalWidth + overhang / 2;
        }
    }
}
