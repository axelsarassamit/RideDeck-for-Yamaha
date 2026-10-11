package com.axelsarassamit.gx12;

import org.junit.Test;
import static org.junit.Assert.*;

public class DashAutoOpenRequestTest {
    private DashAutoOpenRequest request() {
        DashAutoOpenRequest request = new DashAutoOpenRequest();
        request.begin(1, 1000);
        return request;
    }
    @Test public void idleHeartbeatCannotOpen() {
        assertFalse(new DashAutoOpenRequest().complete(0, 1, true, 2000, true, true, true, true));
    }
    @Test public void waitsForServiceThenOpensExactlyOnce() {
        DashAutoOpenRequest request = request();
        assertFalse(request.ready(0, false, 1500));
        assertTrue(request.ready(1, true, 1600));
        assertTrue(request.complete(request.id(), 1, true, 1700, true, true, true, true));
        assertFalse(request.complete(request.id(), 1, true, 1800, true, true, true, true));
    }
    @Test public void pauseEndOrNewSessionInvalidatesDelayedReply() {
        DashAutoOpenRequest request = request();
        assertFalse(request.complete(request.id(), 2, false, 1700, true, true, true, true));
        request = request();
        assertFalse(request.complete(request.id(), 3, true, 1700, true, true, true, true));
    }
    @Test public void leavingUiCancelsEvenIfItReturnsBeforeReply() {
        DashAutoOpenRequest request = request();
        request.cancel();
        assertFalse(request.complete(request.id(), 1, true, 1700, true, true, true, true));
    }
    @Test public void absentUntrustedDisabledOrSettingOffCannotOpen() {
        assertFalse(request().complete(1, 1, true, 1700, true, true, true, false));
        assertFalse(request().complete(1, 1, true, 1700, true, true, false, true));
    }
    @Test public void rejectedAuthenticationAndHiddenUiCannotOpen() {
        assertFalse(request().complete(1, 1, true, 1700, false, true, true, true));
        assertFalse(request().complete(1, 1, true, 1700, true, false, true, true));
    }
    @Test public void stalledAuthenticationExpires() {
        assertFalse(request().complete(1, 1, true, 11000, true, true, true, true));
    }
    @Test public void oldReplyCannotConsumeNewGesture() {
        DashAutoOpenRequest request = request();
        long old = request.id();
        request.begin(3, 2000);
        assertFalse(request.complete(old, 3, true, 2100, true, true, true, true));
        assertTrue(request.complete(request.id(), 3, true, 2200, true, true, true, true));
    }
    @Test public void sessionGenerationSeparatesStartPauseResumeAndEnd() {
        DashRideSessionState session = new DashRideSessionState();
        assertEquals(0, session.generation());
        session.start(0);assertEquals(1, session.generation());
        session.start(1);assertEquals(1, session.generation());
        session.pause(2);assertEquals(2, session.generation());
        session.start(3);assertEquals(3, session.generation());
        session.end();assertEquals(4, session.generation());
    }
}
