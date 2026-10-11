package com.axelsarassamit.gx12;

/** A token can be sent once, during the same still-visible explicit rider action. */
public final class DashStartTokenGate {
    private final long requestedAt;
    private boolean pending = true;
    public DashStartTokenGate(long now) { requestedAt = now; }
    public void cancel() { pending = false; }
    public boolean consume(long now, boolean trustedCreator, boolean safeToken, boolean visible, boolean sameRide, boolean permissions) {
        boolean send = pending && now >= requestedAt && now - requestedAt < 10000
            && trustedCreator && safeToken && visible && sameRide && permissions;
        pending = false;
        return send;
    }
}
