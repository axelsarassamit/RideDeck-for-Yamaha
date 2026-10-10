package com.axelsarassamit.gx12;

/** In-memory user-started ride state. A new process always starts idle. */
public final class DashRideSessionState {
    private boolean active;
    private long accumulated;
    private long startedAt;
    public synchronized boolean active() { return active; }
    public synchronized long elapsed(long now) { return accumulated + (active ? Math.max(0, now - startedAt) : 0); }
    public synchronized void start(long now) { if (!active) { startedAt = now; active = true; } }
    public synchronized void pause(long now) { if (active) { accumulated = elapsed(now); active = false; } }
    public synchronized void end() { active = false; accumulated = 0; startedAt = 0; }
}
