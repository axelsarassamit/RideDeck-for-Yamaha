package com.axelsarassamit.gx12;

/** REC is based only on a fresh camera-state reply, never on a ride lease or start acknowledgement. */
public final class DashRecordingStatus {
    public enum State { IDLE, STARTING, RECORDING, STOPPED, UNAVAILABLE }
    private State state = State.IDLE;
    private long confirmedAt;
    public synchronized void requested(long now) { state = State.STARTING;confirmedAt = now; }
    public synchronized void confirmed(boolean recording, boolean starting, long now) {
        state = recording ? State.RECORDING : starting ? State.STARTING : State.STOPPED;confirmedAt = now;
    }
    public synchronized void unavailable() { if (state != State.IDLE) state = State.UNAVAILABLE; }
    public synchronized void clear() { state = State.IDLE;confirmedAt = 0; }
    public synchronized State state(long now) {
        return state != State.IDLE && (now < confirmedAt || now - confirmedAt >= 3000) ? State.UNAVAILABLE : state;
    }
    public synchronized String label(long now) {
        switch (state(now)) {
            case RECORDING: return "REC · recording";
            case STARTING: return "Starting recording";
            case STOPPED: return "Recording stopped";
            case UNAVAILABLE: return "Recording status unavailable";
            default: return "";
        }
    }
}
