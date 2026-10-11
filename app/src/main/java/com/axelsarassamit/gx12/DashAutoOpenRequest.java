package com.axelsarassamit.gx12;

/** One visible Start/Resume gesture. Never created by a service or heartbeat. */
public final class DashAutoOpenRequest {
    private long generation;
    private long deadline;
    private boolean pending;
    private long id;
    public long id() { return id; }
    public void begin(long expectedGeneration, long now) {
        id++;
        generation = expectedGeneration;
        deadline = now + 10000;
        pending = true;
    }
    public void cancel() { pending = false; }
    public boolean waiting(long now) {
        if (now >= deadline) pending = false;
        return pending;
    }
    public boolean ready(long currentGeneration, boolean active, long now) {
        if (!waiting(now)) return false;
        if (currentGeneration > generation) cancel();
        return pending && currentGeneration == generation && active;
    }
    public boolean complete(long requestId, long currentGeneration, boolean active, long now,
                            boolean authenticated, boolean visible, boolean enabled, boolean installed) {
        if (requestId != id) return false;
        boolean open = ready(currentGeneration, active, now) && authenticated && visible && enabled && installed;
        cancel();
        return open;
    }
}
