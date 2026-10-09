package com.axelsarassamit.gx12;

/** Show current time, remaining time, arrival and distance once each; skip unavailable readouts. */
public final class CockpitClockCycle {
    public enum Mode { TIME, REMAINING_TIME, ETA, DISTANCE }
    public static final long INTERVAL_MILLIS = 5000;
    private CockpitClockCycle() { }
    public static Mode mode(long elapsedMillis, boolean arrivalAvailable, boolean distanceAvailable) {
        int count = 1 + (arrivalAvailable ? 2 : 0) + (distanceAvailable ? 1 : 0);
        int slot = (int) ((Math.max(0, elapsedMillis) / INTERVAL_MILLIS) % count);
        if (slot == 0) return Mode.TIME;
        if (arrivalAvailable && slot == 1) return Mode.REMAINING_TIME;
        if (arrivalAvailable && slot == 2) return Mode.ETA;
        if (distanceAvailable) return Mode.DISTANCE;
        return Mode.TIME;
    }
}
