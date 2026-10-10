package com.axelsarassamit.gx12;

public final class DashBridgeReply {
    private DashBridgeReply() { }
    public static String renewal(int version, Boolean accepted, boolean rideActive) {
        if (version != 1 || accepted == null || (accepted && !rideActive)) return "Dash needs a compatible update";
        return accepted ? "Dash background enabled" : "Another RideDeck session is using Dash";
    }
}
