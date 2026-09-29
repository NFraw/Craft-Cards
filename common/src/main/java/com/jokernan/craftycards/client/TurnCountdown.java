package com.jokernan.craftycards.client;

/**
 * HUD 每轮倒计时的显示算术：<b>纯函数，不碰 MC 类型</b>（所以有单测）。
 *
 * <p>为什么单独抽出来：倒计时是"每秒都在变的一行字"，最容易出的是两类小毛病——
 * 最后 1 秒因为向下取整显示成 0（看起来像卡住），以及读秒与判负对不上。
 * 取整规则与颜色分级都放在这里，改的时候不必去翻渲染代码。</p>
 *
 * <p>约定：{@code remainingTicks < 0} 表示<b>本服务器不限时</b>（{@code server.json} 的
 * {@code turnTimeoutTicks = 0}），HUD 什么都不画；{@code 0} 表示"这一轮已经到点"
 * （服务端最多再等一个清扫节拍就判负），仍然画出来，让玩家看见"就是现在"。</p>
 */
public final class TurnCountdown {
    /** 20 游戏刻 = 1 秒。 */
    private static final int TICKS_PER_SECOND = 20;

    /** 剩余不多于这个秒数就变橙色。 */
    public static final int WARN_SECONDS = 10;
    /** 剩余不多于这个秒数就变红色。 */
    public static final int DANGER_SECONDS = 5;

    public static final int COLOR_NORMAL = 0xFFAAAAAA;
    public static final int COLOR_WARN = 0xFFFFAA00;
    public static final int COLOR_DANGER = 0xFFFF5555;

    private TurnCountdown() {}

    /** 剩余刻数 → 显示秒数，<b>向上取整</b>：还剩 1 刻也要显示 1 秒，不能显示成 0。 */
    public static int secondsLeft(int remainingTicks) {
        if (remainingTicks <= 0) return 0;
        return (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** 倒计时那一段的颜色：越紧越红。 */
    public static int color(int remainingTicks) {
        int seconds = secondsLeft(remainingTicks);
        if (seconds <= DANGER_SECONDS) return COLOR_DANGER;
        if (seconds <= WARN_SECONDS) return COLOR_WARN;
        return COLOR_NORMAL;
    }

    /**
     * 倒计时的显示文本（接在阶段那一行后面，含前导分隔符）；
     * {@code remainingTicks < 0}（不限时）返回空串，调用方据此什么也不画。
     */
    public static String label(int remainingTicks) {
        if (remainingTicks < 0) return "";
        return " | 剩余 " + secondsLeft(remainingTicks) + "s";
    }
}
