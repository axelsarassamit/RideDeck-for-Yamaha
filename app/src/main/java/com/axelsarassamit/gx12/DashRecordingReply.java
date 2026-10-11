package com.axelsarassamit.gx12;

/** Acknowledged request is distinct from actual camera state. */
public final class DashRecordingReply {
    public static final int MAX_JPEG_BYTES = 100000;
    private DashRecordingReply() { }
    public static boolean valid(int version, Boolean accepted) { return version == 1 && accepted != null; }
    public static String status(int version, Boolean accepted, String message) {
        if (!valid(version, accepted)) return "Dash needs a compatible update";
        if (message != null && !message.trim().isEmpty()) return message.substring(0, Math.min(240, message.length()));
        return accepted ? "Recording requested. Check the live camera view." : "Recording was not accepted. Check Dash settings.";
    }
    public static boolean frameAllowed(byte[] jpeg) { return jpeg != null && jpeg.length > 0 && jpeg.length <= MAX_JPEG_BYTES; }
    public static boolean cameraStateValid(Boolean recording, Boolean starting) {
        return recording != null && starting != null && !(recording && starting);
    }
}
