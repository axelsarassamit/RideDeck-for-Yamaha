package com.axelsarassamit.gx12;

/** Voice may resume only the same user-started ride after its foreground screen returns. */
public final class VoiceResumePolicy {
    private VoiceResumePolicy() { }
    public static boolean allow(boolean pausedByVoice, boolean visible, boolean rideActive, long before, long now) {
        return pausedByVoice && visible && rideActive && before==now;
    }
}
