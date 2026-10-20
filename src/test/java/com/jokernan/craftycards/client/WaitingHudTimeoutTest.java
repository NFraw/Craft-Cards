package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaitingHudTimeoutTest {
    @Test void bothWaitingHintsDisappearAtFiveSeconds() {
        for (String hint : new String[]{"no_chips_required", "chips_not_configured"}) {
            var timer = new WaitingHudTimeout();
            timer.update(hint, true, 100);
            assertTrue(timer.visible(100));
            assertTrue(timer.visible(5099));
            assertFalse(timer.visible(5100));
            assertFalse(timer.visible(60_000));
        }
    }

    @Test void repeatedSnapshotsCannotKeepHintAlive() {
        var timer = new WaitingHudTimeout();
        timer.update("table_a:waiting0", true, 100);
        for (long now = 200; now < 10_000; now += 200) timer.update("table_a:waiting0", true, now);
        assertFalse(timer.visible(10_000));
    }

    @Test void rightClickCanShowHintForAnotherFiveSeconds() {
        var timer = new WaitingHudTimeout();
        timer.update("a", true, 0);
        assertFalse(timer.visible(5000));
        timer.showAgain(8000);
        assertTrue(timer.visible(12_999));
        assertFalse(timer.visible(13_000));
    }

    @Test void differentTableOrMeaningfulWaitingChangeStartsANewHint() {
        var timer = new WaitingHudTimeout();
        timer.update("a:waiting0", true, 0);
        timer.update("a:waiting1", true, 6000);
        assertTrue(timer.visible(10_999));
        assertFalse(timer.visible(11_000));
        timer.update("b:waiting0", true, 12_000);
        assertTrue(timer.visible(16_999));
        assertFalse(timer.visible(17_000));
    }

    @Test void activePhasesDoNotUseWaitingTimeout() {
        var timer = new WaitingHudTimeout();
        timer.update("a", true, 0);
        timer.update("a", false, 1000);
        timer.showAgain(2000);
        assertFalse(timer.visible(2000));
        timer.update("a", true, 10_000);
        assertTrue(timer.visible(14_999));
        assertFalse(timer.visible(15_000));
    }

    @Test void resetClearsLifetimeForReconnect() {
        var timer = new WaitingHudTimeout();
        timer.update("a", true, 0);
        timer.reset();
        assertFalse(timer.visible(1));
        timer.update("a", true, 10_000);
        assertTrue(timer.visible(14_999));
        assertFalse(timer.visible(15_000));
    }
}
