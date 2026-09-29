package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每轮倒计时的显示算术。
 *
 * <p>钉住的是两条容易出错、又只能靠眼睛发现的规则：<b>秒数向上取整</b>（否则最后 1 秒显示 0，
 * 看起来像卡住）与<b>"不限时"要能表达出来</b>（{@code -1} → 不画；{@code 0} → 画成 0s，
 * 因为那时服务端马上就要判负了）。</p>
 */
class TurnCountdownTest {

    @Test
    void secondsRoundUpSoTheLastSecondStillShows() {
        assertEquals(30, TurnCountdown.secondsLeft(600));
        assertEquals(1, TurnCountdown.secondsLeft(20));
        // 关键的一条：还剩 1 刻时不能显示成 0
        assertEquals(1, TurnCountdown.secondsLeft(1));
        assertEquals(2, TurnCountdown.secondsLeft(21));
        assertEquals(0, TurnCountdown.secondsLeft(0));
        assertEquals(0, TurnCountdown.secondsLeft(-3));
    }

    @Test
    void unlimitedDrawsNothing() {
        assertEquals("", TurnCountdown.label(-1));
        assertTrue(TurnCountdown.label(600).contains("30"));
        // 到点那一刻仍然要画出来（0s），让玩家看见"就是现在"
        assertTrue(TurnCountdown.label(0).contains("0s"));
    }

    @Test
    void colorTurnsOrangeThenRed() {
        assertEquals(TurnCountdown.COLOR_NORMAL, TurnCountdown.color(600));   // 30 秒
        assertEquals(TurnCountdown.COLOR_NORMAL, TurnCountdown.color(220));   // 11 秒
        assertEquals(TurnCountdown.COLOR_WARN, TurnCountdown.color(200));     // 10 秒
        assertEquals(TurnCountdown.COLOR_WARN, TurnCountdown.color(101));     // 6 秒（向上取整仍是 6）
        assertEquals(TurnCountdown.COLOR_DANGER, TurnCountdown.color(100));   // 5 秒
        assertEquals(TurnCountdown.COLOR_DANGER, TurnCountdown.color(0));
    }
}
