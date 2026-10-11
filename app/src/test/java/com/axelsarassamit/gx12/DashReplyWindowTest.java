package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashReplyWindowTest {
    @Test public void timeoutFreesPollingWithoutAcceptingExpiredReply() {
        DashReplyWindow window = new DashReplyWindow(2000);window.begin(1,1000);
        assertEquals(1,window.cancel());assertFalse(window.pending());
        window.begin(2,3001);assertFalse(window.accept(1,3100));assertTrue(window.pending());
        assertTrue(window.accept(2,3200));assertFalse(window.pending());
    }
    @Test public void pauseAndResumeCannotMistakeOldReplyForNewQuery() {
        DashReplyWindow window = new DashReplyWindow(1000);window.begin(1,1000);window.cancel();
        window.begin(2,1100);assertFalse(window.accept(1,1200));assertTrue(window.accept(2,1200));
    }
    @Test public void duplicateAndLateRepliesCannotRefreshCameraState() {
        DashReplyWindow window = new DashReplyWindow(1000);window.begin(1,1000);
        assertTrue(window.accept(1,1500));assertFalse(window.accept(1,1501));
        window.begin(2,2000);assertFalse(window.accept(2,3000));assertFalse(window.pending());
    }
    @Test public void backwardClockRejectsReplyAndAllowsFreshQuery() {
        DashReplyWindow window = new DashReplyWindow(1000);window.begin(1,1000);
        assertFalse(window.accept(1,999));assertFalse(window.pending());window.begin(2,1100);
        assertTrue(window.accept(2,1200));
    }
}
