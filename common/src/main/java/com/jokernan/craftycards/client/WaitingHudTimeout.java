package com.jokernan.craftycards.client;

/** Client-only display lifetime; hiding a hint must never leave a table or forget its snapshot. */
final class WaitingHudTimeout {
    static final long DURATION_MILLIS = 5000;
    private boolean waiting;
    private Object state;
    private long visibleUntil;

    void update(Object state, boolean waiting, long nowMillis) {
        if (!waiting) {
            reset();
            return;
        }
        if (!this.waiting || !java.util.Objects.equals(this.state, state))
            visibleUntil = nowMillis + DURATION_MILLIS;
        this.waiting = true;
        this.state = state;
    }

    void showAgain(long nowMillis) {
        if (waiting) visibleUntil = nowMillis + DURATION_MILLIS;
    }

    boolean visible(long nowMillis) { return waiting && nowMillis < visibleUntil; }

    void reset() {
        waiting = false;
        state = null;
        visibleUntil = 0;
    }
}
