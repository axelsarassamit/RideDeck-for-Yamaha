package com.axelsarassamit.gx12;

/** One in-flight query. Cancellation/expiry frees the slot without accepting an older reply. */
public final class DashReplyWindow {
    private final long timeout;
    private long pendingId, sentAt;
    public DashReplyWindow(long timeout) { this.timeout = timeout; }
    public boolean pending() { return pendingId != 0; }
    public void begin(long id, long now) { pendingId = id;sentAt = now; }
    public long id() { return pendingId; }
    public long cancel() { long id = pendingId;pendingId = 0;return id; }
    public boolean accept(long id, long now) {
        if (id == 0 || id != pendingId) return false;
        pendingId = 0;
        return now >= sentAt && now - sentAt < timeout;
    }
}
