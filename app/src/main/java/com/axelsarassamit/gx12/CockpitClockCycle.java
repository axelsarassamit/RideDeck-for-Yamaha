package com.axelsarassamit.gx12;

/** Keep current time between the two route readouts; unavailable data returns to time. */
public final class CockpitClockCycle {
    public enum Mode { TIME, ETA, DISTANCE }
    public static final long INTERVAL_MILLIS = 5000;
    private CockpitClockCycle() { }
    public static Mode mode(long elapsedMillis, boolean arrivalAvailable, boolean distanceAvailable) {
        int slot = (int) ((Math.max(0, elapsedMillis) / INTERVAL_MILLIS) % 4);
        if (slot == 1 && arrivalAvailable) return Mode.ETA;
        if (slot == 3 && distanceAvailable) return Mode.DISTANCE;
        return Mode.TIME;
    }
}
