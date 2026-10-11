package com.axelsarassamit.gx12;

/** Acknowledged request is distinct from actual camera state. */
public final class DashRecordingReply {
    public static final int MAX_JPEG_BYTES = 100000;
    private DashRecordingReply() { }
    public static boolean valid(int version, Boolean accepted) { return version == 1 && accepted != null; }
    public static String status(int version, Boolean accepted, String message) {
        if (!valid(version, accepted)) return "Dash needs a compatible update";
        if (message != null && !message.trim().isEmpty()) return displayMessage(message);
        return accepted ? "Recording requested. Check the live camera view." : "Recording was not accepted. Check Dash settings.";
    }
    public static String displayMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "No live image. Check Dash settings.";
        String clean = message.trim().replaceAll("\\s+", " ");
        String lower = clean.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("exception") || lower.contains("android.") || lower.contains("java.")
            || lower.contains("uid=") || lower.contains("targetsdk") || lower.contains("requires permissions"))
            return "Android blocked recording. Keep RideDeck visible and tap Start again.";
        return clean.length() <= 120 ? clean : "Recording unavailable. Check Dash settings and try Start again.";
    }
    public static boolean frameAllowed(byte[] jpeg) { return jpeg != null && jpeg.length > 0 && jpeg.length <= MAX_JPEG_BYTES; }
    public static boolean cameraStateValid(Boolean recording, Boolean starting) {
        return recording != null && starting != null && !(recording && starting);
    }
}
